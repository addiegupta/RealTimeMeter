
package com.server.provision

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Status}
import com.server.provision.PlanDbActor.{FindPlanById, UpdateBalanceById}
import com.server.provision.MeteringActor.{EndCallMeter, Greetings}
import akka.pattern.AskTimeoutException

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MediatorActor{
  def props(planDbActor: ActorRef)(implicit system : ActorSystem) =
    Props(classOf[MediatorActor],planDbActor,system)

  case object EndCallMediator
  case class InitiateMeter(id:Int)
  case class UpdateBalance(id:Int,balance:Int)
  case class ReceivePlanDetails(f:Future[Option[Int]],id:Int)

}
class MediatorActor(planDbActor: ActorRef)(implicit system: ActorSystem) extends Actor
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
      case _: ArithmeticException      => log.info(s"Resuming Meter Actor for id: #$id#")
                                          Resume
      case _: NullPointerException     => log.info(s"Restarting Meter Actor for id: #$id#")
                                          Restart
      case _: IllegalArgumentException => log.info(s"Stopping Meter Actor for id: #$id#")
                                          Stop
      case _: Exception                =>log.info(s"Resuming Meter Actor for id: #$id#")
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

      /*case e: Status.Failure =>
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
                      meterActor ! Greetings
              //          balanceMeterActor ! DecreaseBalance

              case None =>log.info(s"Record Not Found for #$id#")//Success with None
                  context stop self
          }*/

    case InitiateMeter(id)=>
      log.info(s"Initiating meter for id: #$id# inside mediator")
      this.id = id
      planDbActor ! FindPlanById(id)

    case EndCallMediator=>
        log.info(s"Ending call - Mediator to BalanceMeterActor for id: #$id#")
        meterActor ! EndCallMeter
    case UpdateBalance(id,balance)=>
      //      balanceMeterActor!PoisonPill
      planDbActor ! UpdateBalanceById(id,balance)
      context stop self
    case ReceivePlanDetails(f,id)=>
      log.info(s"Waiting for Plan Details for id: #$id#")

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
            log.info(s"Found Valid Plan Details for id: #$id# , Call can be established. Hence, Creating Metering Actor.")

            meterActor = context.actorOf(MeteringActor.props(id, balance), name = s"balanceMeterActor-$id")
            // Greeting sent so that visualmailbox can display the meter actor
            meterActor ! Greetings
          }

        case Failure(exception)=>println("f:"+exception)
      }

      case "crash-meter"=>meterActor!"fail"


  }

}
