package com.normation.plugins.apiauthorizations

import com.normation.rudder.api.ApiAccountId
import com.normation.rudder.api.ApiAccountName
import com.normation.rudder.api.ApiAccountType
import com.normation.rudder.api.ApiAuthorizationKind
import com.normation.rudder.apidata.JsonApiAcl
import com.normation.utils.DateFormaterService
import org.joda.time.DateTime
import zio.json.*

trait UserJsonCodec {

  implicit val accountIdEncoder:         JsonEncoder[ApiAccountId]         = JsonEncoder[String].contramap(_.value)
  implicit val accountNameEncoder:       JsonEncoder[ApiAccountName]       = JsonEncoder[String].contramap(_.value)
  implicit val dateTimeEncoder:          JsonEncoder[DateTime]             = JsonEncoder[String].contramap(DateFormaterService.serialize)
  implicit val accountTypeEncoder:       JsonEncoder[ApiAccountType]       = JsonEncoder[String].contramap(_.name)
  implicit val authorizationTypeEncoder: JsonEncoder[ApiAuthorizationKind] = JsonEncoder[String].contramap(_.name)
  implicit val aclEncoder:               JsonEncoder[JsonApiAcl]           = DeriveJsonEncoder.gen[JsonApiAcl]

}

object UserJsonCodec extends UserJsonCodec
