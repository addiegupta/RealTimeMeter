package com.server.provision


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props}
import com.server.provision.MediatorActor.UpdateBalance
import com.server.provision.MeteringActor.{EndCallMeter, Greetings}

import scala.concurrent.duration._

object MeteringActor
{
  def props(id: Int,balance:Int)(implicit system: ActorSystem)  =
    Props(classOf[MeteringActor],id,balance,system)
  case object EndCallMeter
  case object Greetings

}

class MeteringActor(id: Int, var balance:Int) (implicit system: ActorSystem) extends Actor
  with ActorLogging{
  var cancellable:Cancellable = null
  implicit val ec = system.dispatcher
  override def preStart(): Unit = {
//    if(balance<100)
//      log.warning(s"Low Balance for id: #$id# , Please Top Up balance soon")

    log.info(s"Started Balance meter for id:#$id# with balance: $balance")

    cancellable = context.system.scheduler.schedule(0 seconds, 1 seconds) {

      balance-=1
      log.info(s"ID: #$id# ; Balance: $balance")
      if(balance==100){
        log.warning(s"Low Balance ,Call will disconnect in 100 seconds")
      }
      if(balance==0){
        cancellable.cancel()
        context.parent!UpdateBalance(id,balance)
        context stop self
      }

    }
    //This cancels further Ticks to be sent

  }
  override def receive: Receive = {
    case "fail" =>
      println("Metering actor fails now")
      throw new Exception("I failed")
    case EndCallMeter=>
      log.info(s"Call Ended for id: #$id#")
      cancellable.cancel()
      sender()!UpdateBalance(id,balance)
      context stop self
    case Greetings =>
      log.info("Received greetings from mediator in meter")
  }
  override def postStop(): Unit = {
    log.info(s"Stopped Balance meter for id: #$id#")
    cancellable.cancel()
  }
}
