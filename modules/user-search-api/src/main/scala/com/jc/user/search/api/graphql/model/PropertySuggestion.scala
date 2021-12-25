package com.jc.user.search.api.graphql.model

final case class PropertySuggestion(property: String, suggestions: Seq[TermSuggestion])
