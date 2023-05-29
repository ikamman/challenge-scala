package com.challenge

import cats._
import cats.effect._
import cats.effect.std._
import cats.implicits._
import com.challenge.FetchService
import fs2._

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

import Api._

trait TaskService[F[_]] {
  def schedule(task: Api.Task): F[Api.TaskId]
  def get(id: Api.TaskId): F[Result[Stream[F, Api.TaskStats]]]
  def cancel(id: Api.TaskId): F[Result[Unit]]
  def start(): Stream[F, Unit]
}

object TaskService {

  def create[F[+_]: Async](fetchService: FetchService[F]): F[TaskService[F]] =
    for {
      queue <- Queue.unbounded[F, Api.Task]
      cache <- new ConcurrentHashMap[TaskId, TaskWorker[F]]().asScala.pure[F]
    } yield new TaskService[F] {

      def schedule(task: Api.Task): F[Api.TaskId] =
        for {
          worker <- TaskWorker.create[F](task, fetchService.stream)
          _ <- cache.addOne((task.id, worker)).pure[F]
          _ <- queue.offer(task)
        } yield task.id

      def get(id: Api.TaskId): F[Result[Stream[F, Api.TaskStats]]] =
        cache.get(id) match {
          case Some(result) => result.streamUpdates.asRight.pure[F]
          case _            => TaskNotFound.asLeft.pure[F]
        }

      def cancel(id: Api.TaskId): F[Result[Unit]] =
        cache.get(id) match {
          case Some(w) =>
            w.update.get.flatMap { stats =>
              if (stats.state.cancellable) w.cancel.map(_.asRight)
              else TaskUncancellable(stats).asLeft.pure[F]
            }
          case _ => TaskNotFound.asLeft.pure[F]
        }

      def start(): Stream[F, Unit] =
        Stream
          .fromQueueUnterminated(queue, 1)
          .parEvalMap(2)(task =>
            cache.get(task.id) match {
              case Some(w) => w.run()
              case None    => Applicative[F].pure().void
            }
          )
    }
}
