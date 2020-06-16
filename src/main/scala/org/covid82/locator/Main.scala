package org.covid82.locator

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.syntax.functor._

object Main extends IOApp {
  val config: FtpConfig = FtpConfig(
    path = "/pub/stats/ripencc/delegated-ripencc-latest",
    host = "ftp.ripe.net", port = 21,
    user = "anonymous", pass = ""
  )

  override def run(args: List[String]): IO[ExitCode] = Blocker[IO].use { blocker =>
    AppServer
      .stream[IO](config, blocker)
      .compile
      .drain
      .as(ExitCode.Success)
  }
}