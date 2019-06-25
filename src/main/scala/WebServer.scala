import akka.Done
import akka.actor.{ActorRef, ActorSystem, InvalidActorNameException, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}
import akka.util.Timeout
import play.api.libs.json.Json

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object WebServer {

  implicit val system = ActorSystem("ServerSample2")
  implicit def executor: ExecutionContext = system.dispatcher
  protected val log = Logging(system.eventStream, "CallServer-main")
  protected implicit val materializer: ActorMaterializer = ActorMaterializer()


  object Messages  {

    def parse(messages: immutable.Seq[String]): Seq[CallerContainer] = messages.map { message =>
      implicit val callerDataFormat = Json.format[CallerContainer]
      Json.parse(message).as[CallerContainer]
    }

    def ack(aString: String) = TextMessage(Source.single("Ack from server: " + aString))
  }

  var callerActor: ActorRef = null
  def main(args: Array[String]): Unit = {
    val endpoint = "ws://127.0.0.1:8080"

    var idCount =0
    val handler = system.actorOf(Props[CallHandler],"handler")

    //compute intermediate sums (= number of measurements) and send them to the Total actor, at least every second
    val usersWebSocketFlow: Flow[Message, Message, Any] =
      Flow[Message]
        .collect {
          case TextMessage.Strict(text) =>
            Future.successful(text)
          case TextMessage.Streamed(textStream) =>
            textStream.runFold("")(_ + _)
              .flatMap(Future.successful)
        }
        .mapAsync(1)(x => x) //identity function
        .groupedWithin(100, 1.second)
        .map(messages => (messages.last, Messages.parse(messages)))
        .map { elem => println(s"Size is: ${elem._2.size}"); elem }
        .mapAsync(1) {
          case (lastMessage: String, users: Seq[CallerContainer]) =>
            import akka.pattern.ask
            implicit val askTimeout = Timeout(30.seconds)
            //only send a single message at a time to the Total actor, backpressure otherwise
            (handler ? HandleData(users.size, users.head.id))
              .mapTo[Done]
              .map(_ => lastMessage)
        }
        .map(Messages.ack)

    val createCallerRoute =
      get {
        path("create-caller") {
          idCount += 1
          try {
            callerActor = system.actorOf(CallerActor.props(idCount, endpoint), idCount.toString)
          } catch {
            case e: InvalidActorNameException => log.info(e.toString)
          }
          val timeStamp = System.currentTimeMillis() / 1000
          complete(StatusCodes.OK, CallerNewContainer(idCount, timeStamp))
        } ~
            path("start-call" / IntNumber / IntNumber) { (callerId, receiverId) =>
              //          val processActor = system.actorOf(ProcessActor.props(1,callerActor,receiver))
              //          processActor ! StartCall

              complete {
                initiateCall(callerId,receiverId)
                s"Received call request to start call from user $callerId to $receiverId"
              }
            } ~
            path("total-users") {
              complete(s"Total number of users is $idCount")
            }
      }

    def initiateCall(callerId: Int, receiverId: Int): Unit ={

    }
    val httpInterface = "127.0.0.1"

    val httpPort = 8080


    log.info(s"About to bind to: $httpInterface and: $httpPort")
    val bindingFuture: Future[ServerBinding] = Http().bindAndHandle(createCallerRoute, httpInterface, httpPort)


    bindingFuture.map { serverBinding =>
      log.info(s"Bound to: ${serverBinding.localAddress} ")
    }.onComplete {
      case Success(value) => log.info("CallServer started successfully")
      case Failure(ex) =>
        log.error(ex, "Failed to bind to {}:{}!", httpInterface, httpPort)
        Http().shutdownAllConnectionPools()
        system.terminate()
    }


    scala.sys.addShutdownHook {
      log.info("Terminating...")
      Http().shutdownAllConnectionPools()
      system.terminate()
      Await.result(system.whenTerminated, 30.seconds)
      log.info("Terminated... Bye")
    }

  }
}
