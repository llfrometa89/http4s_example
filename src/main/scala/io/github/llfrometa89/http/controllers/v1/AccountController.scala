package io.github.llfrometa89.http.controllers.v1

import cats.effect.Sync
import cats.implicits._
import io.github.llfrometa89.application.dto.CreateAccount.AccountRequest
import io.github.llfrometa89.application.dto.Transfer.TransferRequest
import io.github.llfrometa89.application.services.AccountApplicationService
import io.github.llfrometa89.http.controllers._
import io.github.llfrometa89.implicits._
import io.github.llfrometa89.http.core.Controller
import io.github.llfrometa89.http.middleware.{Auth111, AuthedService111, RequestContext, RequestContextInterceptor}
import io.github.llfrometa89.http.middleware.RequestContext.RequestContextMiddleware
import org.http4s.{AuthedService, BasicCredentials, HttpRoutes}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.server.middleware.authentication.BasicAuth.BasicAuthenticator

trait Http4sDsl111[F[_]] extends Http4sDsl[F] with Auth111

object AccountController extends Controller {

  def routes[F[_]: Sync]: HttpRoutes[F] = {

    val dsl = new Http4sDsl111[F] {}
    import dsl._

    val authStore: BasicAuthenticator[F, String] = (creds: BasicCredentials) =>
      if (creds.username == "username" && creds.password == "password") Sync[F].pure(Some(creds.username))
      else Sync[F].pure(None)

    val basicAuth: AuthMiddleware[F, String] = BasicAuth("realm.example.com", authStore)

    val requestContext: RequestContextMiddleware[F, RequestContext] = RequestContextInterceptor()

    def authRoutes: HttpRoutes[F] =
      basicAuth(AuthedService[String, F] {
        case GET -> Root / "protected" as user =>
          Ok(s"This page is protected using HTTP authentication; logged in as $user")
      })

    def requestContextRoutes: HttpRoutes[F] = requestContext(
      AuthedService111[RequestContext, F] {
        case GET -> Root / "protected2" as111 rc => Ok(s".........>>>>  rc = $rc")
      }
    )

    val defaultRoutes = HttpRoutes.of[F] {
      case req @ POST -> Root / ACCOUNTS =>
        for {
          accountRequest <- req.as[AccountRequest]
          account        <- AccountApplicationService.open[F](accountRequest)
          resp           <- Ok(account)
        } yield resp
      case req @ POST -> Root / ACCOUNTS / TRANSFER =>
        for {
          transferRequest <- req.as[TransferRequest]
          result          <- AccountApplicationService.transfer[F](transferRequest)
          resp            <- Ok(result)
        } yield resp
    }

    defaultRoutes <+> authRoutes <+> requestContextRoutes
  }
}
