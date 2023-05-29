package com.challenge

import io.circe.Json
import io.circe.jawn.CirceSupportParser
import org.http4s.Request
import org.http4s.Method
import org.http4s.Uri
import cats.effect.kernel.Async
import org.http4s.client.Client
import fs2.Stream
import fs2.Pipe
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait FetchService[F[_]] {
  def stream(uri: String): Stream[F, Map[String, String]]
}

object FetchService {

  def splitLine[F[_]](sep: Char): Pipe[F, String, Vector[String]] =
    _.map(_.split(sep.toString, -1).toVector.map(_.trim))

  def zipWithHeader[F[_]]: Pipe[F, Vector[String], Map[String, String]] =
    csvRows =>
      csvRows.zipWithIndex
        .mapAccumulate(Option.empty[Vector[String]]) {
          case (None, (headerRow, _)) =>
            (Some(headerRow), Map.empty[String, String])
          case (h @ Some(header), (row, _)) => h -> header.zip(row).toMap
        }
        .drop(1)
        .map(_._2)

  def impl[F[_]: Async](client: Client[F]): FetchService[F] =
    new FetchService[F] {

      val logger: Logger[F] = Slf4jLogger.getLogger[F]

      def stream(uri: String): Stream[F, Map[String, String]] = {
        val req =
          Request[F](
            Method.GET,
            Uri.unsafeFromString(uri)
          )
        client
          .stream(req)
          .flatMap(res => res.body.chunks)
          .through(fs2.text.utf8.decodeC)
          .through(fs2.text.lines)
          .through(splitLine(','))
          .through(zipWithHeader)
      }
    }
}
