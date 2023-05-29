package com.challenge
import cats.effect._
import com.challenge.FetchService
import com.comcast.ip4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import pureconfig.ConfigSource
import pureconfig.generic.auto._

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
