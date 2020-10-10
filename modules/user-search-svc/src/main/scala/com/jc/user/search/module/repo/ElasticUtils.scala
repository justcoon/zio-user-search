package com.jc.user.search.module.repo

import com.sksamuel.elastic4s.ElasticError

object ElasticUtils {

  def getReason(error: ElasticError): String = {

    val reasons = error.failedShards.flatMap(_.reason.map(_.reason).toList)

    if (reasons.nonEmpty) {
      reasons.mkString(", ")
    } else {
      error.reason
    }
  }

  def getTermSuggestionName(propertyName: String): String = {
    s"${propertyName}_term"
  }

  val SuggestPropertySuffix: String = "_sugg"

  def getSuggestPropertyName(propertyName: String): String = {
    s"${propertyName}${SuggestPropertySuffix}"
  }

  def getPropertyNameFromSuggest(propertyName: String): String = {
    propertyName.endsWith(SuggestPropertySuffix) match {
      case true => propertyName.substring(0, propertyName.length - SuggestPropertySuffix.length)
      case false => propertyName
    }
  }
}
