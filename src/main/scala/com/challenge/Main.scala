package com.challenge

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple {
  val run = TaskServer.run[IO]
}
