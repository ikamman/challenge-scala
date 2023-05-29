package com.challenge

import cats.effect.IO
import cats.effect.IOApp

object Main extends IOApp.Simple {
  val run = TaskServer.run[IO]
}
