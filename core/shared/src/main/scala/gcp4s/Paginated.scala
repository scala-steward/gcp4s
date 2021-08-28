/*
 * Copyright 2021 Arman Bilge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gcp4s

import cats.MonadThrow
import cats.syntax.all.given
import fs2.Stream
import org.http4s.EntityDecoder
import org.http4s.Method
import org.http4s.Request
import org.http4s.client.Client

trait Paginated[-A]:
  extension [A](paginated: A) def pageToken: Option[String]

object Paginated:
  private val pageToken = "pageToken"

  def apply[F[_]: MonadThrow, A: Paginated](
      req: Request[F])(using client: Client[F], decoder: EntityDecoder[F, A]): Stream[F, A] =
    req.method match
      case Method.GET =>
        val initialPageToken = req.uri.query.pairs.find(_._1 == pageToken).flatMap(_._2)
        val uri = req.uri.removeQueryParam(pageToken)
        Stream.unfoldLoopEval(uri.withOptionQueryParam(pageToken, initialPageToken)) { uri =>
          client.expect[A](req.withUri(uri)).map { a =>
            (a, a.pageToken.map(uri.withQueryParam(pageToken, _)))
          }
        }
      case _ =>
        Stream.raiseError(
          new IllegalArgumentException("Paginated request must be a GET request"))