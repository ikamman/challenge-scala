package com.challenge

import Api._
import cats._
import cats.implicits._
import cats.effect._

trait ApiService[F[_]] {
  def newTask(req: NewTask): F[TaskId]
  def getTask(id: TaskId): F[Result[fs2.Stream[F, TaskStats]]]
  def cancel(id: TaskId): F[Result[Unit]]
}

object ApiService {
  def impl[F[_]: Async](taskService: TaskService[F], config: AppConfig) =
    new ApiService[F] {

      override def cancel(id: TaskId): F[Result[Unit]] = taskService.cancel(id)

      override def getTask(id: TaskId): F[Result[fs2.Stream[F, TaskStats]]] =
        taskService.get(id)

      def newTask(req: NewTask): F[TaskId] = for {
        id <- TaskId.generate[F]
        task = Task(id, req.uri, s"${config.location}/json/${id.value}.json")
        _ <- taskService.schedule(task)
      } yield id
    }
}
