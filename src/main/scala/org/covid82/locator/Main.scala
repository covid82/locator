package org.covid82.locator

import cats.effect.{IO, IOApp, ExitCode}
import cats.syntax.functor._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    AppServer
      .stream[IO]
      .compile
      .drain
      .as(ExitCode.Success)
}
