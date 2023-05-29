package com.challenge

import cats._
import cats.effect._
import cats.effect.kernel.Resource.ExitCase.Canceled
import cats.effect.kernel.Resource.ExitCase.Errored
import cats.effect.kernel.Resource.ExitCase.Succeeded
import cats.implicits._
import fs2.Pipe
import fs2.Stream
import fs2.concurrent.SignallingRef
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.text
import io.circe.Json
import io.circe.parser.{parse => parseJson}
import monocle.macros.syntax.lens._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._

import TaskWorker._

case class TaskWorker[F[_]: Async](
    task: Api.Task,
    signal: SignallingRef[F, Boolean],
    fetch: Fetch[F],
    update: Ref[F, Api.TaskStats]
) {

  val logger: Logger[F] = Slf4jLogger.getLogger[F]

  def streamUpdates: Stream[F, Api.TaskStats] =
    Stream
      .eval(update.get)
      .flatMap(stats =>
        if (stats.state.terminal) Stream.emit(stats)
        else
          Stream
            .awakeEvery(2.seconds)
            .evalMap(_ => update.get)
            .interruptWhen(
              Stream
                .repeatEval(update.get)
                .map(_.state.terminal)
                .delayBy(1.second)
            )
            .onComplete(Stream.eval(update.get))
      )

  def cancel: F[Unit] = for {
    _ <- signal.set(true)
    _ <- update.getAndUpdate(_.lens(_.state).set(Api.State.Canceled))
  } yield ()

  def run(): F[Unit] =
    Stream
      .eval(update.get)
      .filter(task => task.state == Api.State.Scheduled)
      .evalTap(_ =>
        update
          .update(_.lens(_.state).set(Api.State.Running))
      )
      .flatMap(_ => fetch(task.inUri))
      .interruptWhen(signal)
      .through(withStats)
      .evalTap { case (stats, _) =>
        update
          .updateAndGet(last =>
            last
              .lens(_.lines)
              .set(stats.lines)
              .lens(_.linesPerSec)
              .set(stats.perSec)
          )
      }
      .map(_._2)
      .map(kv =>
        kv.map { case (k, v) => (k, parseJson(v).getOrElse(Json.Null)) }
      )
      .map(props => Json.obj(props.toSeq: _*).noSpaces)
      .through(text.utf8.encode)
      .through(Files[F].writeAll(Path(s"${task.id.value}.json")))
      .onFinalizeCase { finalCase =>
        (finalCase match {
          case Succeeded =>
            update
              .update { stats =>
                if (stats.state.cancellable)
                  stats
                    .lens(_.state)
                    .set(Api.State.Done)
                    .lens(_.outUri)
                    .set(Some(task.outUri))
                else stats
              }
          case Errored(_) =>
            update
              .update(_.lens(_.state).set(Api.State.Failed))
          case Canceled =>
            update
              .update(_.lens(_.state).set(Api.State.Canceled))
        }).void
      }
      .compile
      .drain
}

object TaskWorker {

  type Fetch[F[_]] = String => Stream[F, Map[String, String]]

  case class Stats(lines: Long, perSec: Double) {
    def calc(d: FiniteDuration): Stats = {
      val l = lines + 1
      val sec = d.toNanos / 1000 / 1000 / 1000
      Stats(l, l.toDouble / sec)
    }
  }
  object Stats {
    def init(): Stats = Stats(0, 0)
  }

  def withTime[F[_]: Clock: Functor, A]: Pipe[F, A, (FiniteDuration, A)] =
    stream =>
      Stream
        .eval(Clock[F].monotonic)
        .flatMap(t0 =>
          stream.evalMap(Clock[F].monotonic.map(_ - t0).tupleRight)
        )

  def withStats[F[_]: Clock: Applicative, A]: Pipe[F, A, (Stats, A)] =
    _.through(withTime)
      .mapAccumulate(Stats.init())((stats, v) => (stats.calc(v._1), v._2))

  def create[F[_]: Async](task: Api.Task, fetch: Fetch[F]): F[TaskWorker[F]] =
    for {
      sig <- SignallingRef.of(false)
      update <- Ref.of(Api.TaskStats.init(task))
    } yield TaskWorker[F](task, sig, fetch, update)
}
