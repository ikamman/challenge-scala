package com.challenge

import cats.Applicative
import cats.effect.std.UUIDGen
import cats.implicits._

object Api {
  import enumeratum._

  sealed trait State extends EnumEntry {
    def cancellable: Boolean = State.cancelable.contains(this)
    def terminal: Boolean = !this.cancellable
  }

  object State extends Enum[State] {

    val values = findValues

    val cancelable = Scheduled :: Running :: Nil

    case object Scheduled extends State
    case object Running extends State
    case object Done extends State
    case object Failed extends State
    case object Canceled extends State
  }

  case class NewTask(uri: String) extends AnyVal

  case class TaskId(value: String) extends AnyVal

  object TaskId {
    def generate[F[_]: Applicative: UUIDGen]: F[TaskId] =
      UUIDGen.randomUUID[F].map(id => TaskId(id.toString()))
  }

  case class TaskStats(
      id: TaskId,
      lines: Long,
      linesPerSec: Double,
      state: State,
      outUri: Option[String]
  )

  object TaskStats {
    def init(task: Task) =
      TaskStats(task.id, 0, 0, State.Scheduled, None)
  }

  case class Task(
      id: TaskId,
      inUri: String,
      outUri: String
  )

  sealed trait TaskError
  case class TaskUncancellable(stats: Api.TaskStats) extends TaskError
  case object TaskNotFound extends TaskError

  type Result[A] = Either[TaskError, A]
}
