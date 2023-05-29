package com.challenge
import cats._
import cats.implicits._
import cats.effect._
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger
import org.http4s.ember.client.EmberClientBuilder
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import com.challenge.FetchService
import org.http4s.server.websocket.WebSocketBuilder2

object TaskServer {

  def run[F[+_]: Async]: F[Nothing] = {

    for {
      client <- EmberClientBuilder.default[F].build
      config = ConfigSource.default.loadOrThrow[AppConfig]
      fetchService = FetchService.impl[F](client)
      taskService <- Resource.eval(TaskService.create[F](fetchService))
      _ <- Resource.eval(taskService.start().compile.drain).start
      apiService = ApiService.impl[F](taskService, config)
      _ <- EmberServerBuilder
        .default[F]
        .withHost(ipv4"0.0.0.0")
        .withPort(Port.fromInt(config.port).getOrElse(port"8080"))
        .withHttpWebSocketApp(wsb => Routes.routes[F](wsb, apiService))
        .build
    } yield ()
  }.useForever
}
