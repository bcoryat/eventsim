package com.interana.eventsim

import com.interana.eventsim.TimeUtilities._
import com.interana.eventsim.buildin.RandomSongGenerator
import org.joda.time.DateTime

/**
 * Created by jadler on 9/4/14.
 *
 * Object to capture session related calculations and properties
 *
 */
class Session(var nextEventTimeStamp: Option[DateTime],
              val alpha: Double, // alpha = expected request inter-arrival time
              val beta: Double,  // beta  = expected session inter-arrival time
              val initialStates: scala.collection.Map[(String,String),WeightedRandomThingGenerator[State]],
              val auth: String,
              val level: String ) {

  val sessionId = Counters.nextSessionId
  var itemInSession = 0
  var done = false
  var currentState:State = initialStates((auth, level)).randomThing
  var currentSong:Option[(String,String,String,Double)] =
    if (currentState.page=="NextSong") Some(RandomSongGenerator.randomThing) else None
  var currentSongEnd:Option[DateTime] =
    if (currentState.page=="NextSong") Some(nextEventTimeStamp.get.plusSeconds(currentSong.get._4.toInt)) else None

  def incrementEvent() = {
    val nextState = currentState.nextState(rng)
    nextState match {
      case None => {
        //nextEventTimeStamp=None
        done=true
      }
      case x if 300 until 399 contains x.get.status => {
        nextEventTimeStamp=Some(nextEventTimeStamp.get.plusSeconds(1))
        currentState = nextState.get
        itemInSession += 1
      }
      case x if x.get.page=="NextSong" => {
        if (currentSong.isEmpty) {
          nextEventTimeStamp=Some(nextEventTimeStamp.get.plusSeconds(exponentialRandomValue(alpha).toInt))
        } else if (nextEventTimeStamp.get.isBefore(currentSongEnd.get)) {
          nextEventTimeStamp = currentSongEnd
        } else {
          nextEventTimeStamp=Some(nextEventTimeStamp.get.plusSeconds(exponentialRandomValue(alpha).toInt))
        }
        currentSong = Some(RandomSongGenerator.randomThing)
        currentSongEnd = Some(nextEventTimeStamp.get.plusSeconds(currentSong.get._4.toInt))
        currentState = nextState.get
        itemInSession += 1
      }
      case _ => {
        nextEventTimeStamp=Some(nextEventTimeStamp.get.plusSeconds(exponentialRandomValue(alpha).toInt))
        currentState = nextState.get
        itemInSession += 1
      }
    }

    /*
    if (nextState.nonEmpty) {

      val nextTimeStamp = nextEventTimeStamp.get.plusSeconds(
        nextState.get match {
        case x if 300 until 399 contains x.status => 1
        case x if x.page=="NextSong" => 1
        case _ => exponentialRandomValue(alpha).toInt
      })

      //val nextTimeStamp =
      //  nextEventTimeStamp.get.plusSeconds(
      //if (nextState.get.status >= 300 && nextState.get.status <= 399) 1 else exponentialRandomValue(alpha).toInt)
      nextEventTimeStamp = Some(nextTimeStamp)
      currentState = nextState.get
      itemInSession += 1
    } else {
      done = true
    }
    */
  }

  def nextSession =
    new Session(Some(Session.pickNextSessionStartTime(nextEventTimeStamp.get, beta)),
      alpha, beta, initialStates, currentState.auth, currentState.level)

}

object Session {

  def pickFirstTimeStamp(st: DateTime,
    alpha: Double, // alpha = expected request inter-arrival time
    beta: Double  // beta  = expected session inter-arrival time
   ): DateTime = {
    // pick random start point, iterate to steady state
    val startPoint = st.minusSeconds(beta.toInt * 2)
    var candidate = pickNextSessionStartTime(startPoint, beta)
    while (new DateTime(candidate).isBefore(new DateTime(st).minusSeconds(beta.toInt))) {
      candidate = pickNextSessionStartTime(candidate, beta)
    }
    candidate
  }

  def pickNextSessionStartTime(lastTimeStamp: DateTime, beta: Double): DateTime = {
    val randomGap = exponentialRandomValue(beta).toInt + SiteConfig.sessionGap
    val nextTimestamp: DateTime = TimeUtilities.standardWarp(lastTimeStamp.plusSeconds(randomGap))
    assert(randomGap > 0)

    if (nextTimestamp.isBefore(lastTimeStamp)) {
      // force forward progress
      pickNextSessionStartTime(lastTimeStamp.plusSeconds(SiteConfig.sessionGap), beta)
    } else if (keepThisDate(lastTimeStamp, nextTimestamp)) {
      nextTimestamp
    } else
      pickNextSessionStartTime(nextTimestamp, beta)
  }
}
