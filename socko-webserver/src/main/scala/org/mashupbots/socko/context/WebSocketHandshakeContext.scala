//
// Copyright 2012 Vibul Imtarnasan, David Bolton and Socko contributors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package org.mashupbots.socko.context

import java.nio.charset.Charset
import java.util.Date

import org.jboss.netty.channel.Channel
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.util.CharsetUtil
import org.mashupbots.socko.utils.WebLogEvent

/**
 * Context for processing web socket handshakes.
 *
 * Socko requires that this context be processed in your route and NOT passed to processor actors.
 * The only action that needs to be taken is to call `ctx.authorize()`.
 *
 * {{{
 * val routes = Routes({
 *   case ctx @ Path("/snoop/websocket/") => ctx match {
 *     case ctx: WebSocketHandshakeContext => {
 *       ctx.authorize()
 *     }
 *     case ctx: WebSocketFrameContext => {
 *       myActorSystem.actorOf(Props[MyWebSocketFrameProcessor], name) ! ctx
 *     }
 *   }
 * })
 * }}}
 *
 * Calling `ctx.authorize()` triggers, Socko to perform all the necessary handshaking. If not called,
 * Socko will reject the handshake and web sockets processing will be aborted.
 *
 * `ctx.authorize()` has been added as a security measure to make sure that upgrade to web sockets is only performed at
 * explicit routes.
 *
 * @param channel Channel by which the request entered and response will be written
 * @param nettyHttpRequest HTTP request associated with the upgrade to web sockets connection
 * @param config Processing configuration
 */
case class WebSocketHandshakeContext(
  channel: Channel,
  nettyHttpRequest: HttpRequest,
  config: HttpContextConfig) extends HttpContext {

  /**
   * Incoming HTTP request
   */
  val request = CurrentHttpRequestMessage(nettyHttpRequest)

  /**
   * Always s set to `null` because no response is available for handshakes. Let the handershaker do the work for you.
   */
  val response = null
  
  /**
   * HTTP end point
   */
  val endPoint = request.endPoint

  private var _isAuthorized: Boolean = false

  private var _authorizedSubprotocols: Option[String] = None

  /**
   * Authorize this web socket handshake to proceed
   *
   * @param subprotocol Comma separated list of supported protocols. e.g. `chat, stomp`
   */
  def authorize(subprotocols: Option[String] = None) {
    _isAuthorized = true
    _authorizedSubprotocols = subprotocols
  }

  /**
   * Indicates if this web socket handshake is authorized or not
   */
  def isAuthorized: Boolean = {
    _isAuthorized
  }

  /**
   * Comma separated list of supported protocols. e.g. `chat, stomp`
   */
  def authorizedSubprotocols: Option[String] = {
    _authorizedSubprotocols
  }

  /**
   * Adds an entry to the web log
   *
   * @param responseStatusCode HTTP status code
   * @param responseSize length of response content in bytes
   */
  def writeWebLog(responseStatusCode: Int, responseSize: Long) {
    if (config.webLog.isEmpty) {
      return
    }

    config.webLog.get.enqueue(WebLogEvent(
      this.createdOn,
      channel.getRemoteAddress,
      channel.getLocalAddress,
      username,
      request.endPoint.method,
      request.endPoint.uri,
      request.contentLength,
      responseStatusCode,
      responseSize,
      duration,
      request.httpVersion,
      request.headers.get(HttpHeaders.Names.USER_AGENT),
      request.headers.get(HttpHeaders.Names.REFERER)))
  }
}