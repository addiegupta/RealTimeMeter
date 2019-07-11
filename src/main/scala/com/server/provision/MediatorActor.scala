
package com.server.provision

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Status}
import akka.stream.ActorMaterializer
import com.server.provision.PlanDbActor.{FindPlanById, UpdateBalanceById}
import com.server.provision.MeteringActor.EndCallMeter
import akka.pattern.AskTimeoutException

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MediatorActor{
  def props(planDbActor: ActorRef)(implicit materializer: ActorMaterializer,system : ActorSystem) =
    Props(classOf[MediatorActor],planDbActor,materializer,system)

  case object EndCallMediator
  case class InitiateMeter(id:Int)
  case class UpdateBalance(id:Int,balance:Int)
  case class ReplyToMeter(f:Future[Option[Int]],id:Int)

}
class MediatorActor(planDbActor: ActorRef)(implicit materializer: ActorMaterializer, system: ActorSystem) extends Actor
with ActorLogging{
  import MediatorActor._

  implicit val ec = system.dispatcher
    var meterActor:ActorRef=null
    var id :Int= 0

  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy() {
      case _: ArithmeticException      => log.info("Resuming Meter Actor")
                                          Resume
      case _: NullPointerException     => log.info("Restarting Meter Actor")
                                          Restart
      case _: IllegalArgumentException => log.info("Stopping Meter Actor")
                                          Stop
      case _: Exception                =>log.info("Resuming Meter Actor")
                                          Resume

    }

  override def preStart(): Unit = {
    log.info("Started new MediatorActor")

  }
  override def postStop(): Unit = {
    log.info("Stopped MediatorActor")
  }

  def show(x: Option[Int]):Int = x match {
    case Some(s) => s
    case None => -1
  }
  override def receive: Receive = {

      case e: Status.Failure =>
          log.info(s" Received status failure in mediator ${e.toString}")
      case e:AskTimeoutException =>
          log.info(s" Received timeout exception in mediator ${e.toString}")
      case value: Option[Int] =>
          log.info(s"Received value from db balance : $value")

          value match {
              case x:Some[Int] =>

                  val balance:Int=show(x)
                  if(balance==0)
                  {
                      log.info(s"Balance 0 for id:#$id# , Call Cannot be established")
                      context stop self
                  }
                  else
                      meterActor = context.actorOf(MeteringActor.props(id,balance), name = s"balanceMeterActor-$id")
              //          balanceMeterActor ! DecreaseBalance

              case None =>log.info(s"Record Not Found for #$id#")//Success with None
                  context stop self
          }

    case InitiateMeter(id)=>
      log.info(s"Initiating meter for id: #$id# inside mediator")
      this.id = id
      planDbActor ! FindPlanById(id)

    case EndCallMediator=>
        log.info(s"Ending call Mediator to BalanceMeterActor for id: #$id#")
        meterActor ! EndCallMeter
    case UpdateBalance(id,balance)=>
      //      balanceMeterActor!PoisonPill
      planDbActor ! UpdateBalanceById(id,balance)
      context stop self
    case ReplyToMeter(f,id)=>
      log.info(s"ReplyToMeter called for id: #$id# ")

//      f.map {
//        case x:Some[Int] =>
//
//          val balance:Int=show(x)
//          if(balance==0)
//          {
//            log.info(s"Balance 0 for id:$id , Call Cannot be established")
//            context stop self
//          }
//          else
//            balanceMeterActor = context.actorOf(MeteringActor.props(id,balance), name = s"balanceMeterActor-$id")
//        //          balanceMeterActor ! DecreaseBalance
//
//        case None =>log.info(s"Record Not Found for $id")//Success with None
//                    context stop self
//
//      }

      f.onComplete{
        case Success(v)=>
          val balance:Int=show(v)
          if(balance==0)
          {
            log.info(s"Balance 0 for id: #$id# , Call Cannot be established")
            context stop self
          }
          else if(balance<0)
          {
            log.info(s"Record Not Found for #$id#")//Success with None
            context stop self
          }
          else {
            meterActor = context.actorOf(MeteringActor.props(id, balance), name = s"balanceMeterActor-$id")
          }

        case Failure(exception)=>println("f:"+exception)
      }

      case "crash-meter"=>meterActor!"fail"


  }

}
