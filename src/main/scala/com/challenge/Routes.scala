package com.challenge

import cats.effect._
import cats.implicits._

import org.http4s.headers._
import org.http4s.MediaType
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.implicits._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import io.circe.generic.auto._
import org.http4s.headers._
import org.http4s.MediaType

import Api._
import org.http4s.dsl.Http4sDsl
import org.http4s.websocket.WebSocketFrame
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.server.staticcontent._
import fs2.io.file.Path
import fs2.Stream

object Routes {

  def routes[F[_]: Async](
      wsb: WebSocketBuilder2[F],
      apiService: ApiService[F]
  ) = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes
      .of[F] {
        case req @ POST -> Root / "task" =>
          for {
            req <- req.as[NewTask]
            resp <- Ok(apiService.newTask(req))
          } yield (resp)
        case GET -> Root / "task" / id =>
          apiService.getTask(TaskId(id)).flatMap {
            case Left(err) =>
              NotFound(err)
            case Right(value) =>
              wsb.build(
                value
                  .map(stats => WebSocketFrame.Text(stats.asJson.spaces2))
                  .onComplete(Stream.emit(WebSocketFrame.Close())),
                _.drain
              )
          }
        case DELETE -> Root / "task" / id =>
          apiService.cancel(TaskId(id)).flatMap {
            case Left(err) => NotFound(err)
            case Right(_)  => Ok()
          }
        case req @ GET -> Root / "json" / file =>
          StaticFile
            .fromPath(fs2.io.file.Path(s"./$file"), Some(req))
            .getOrElseF(NotFound())
      }
      .orNotFound
  }
}
