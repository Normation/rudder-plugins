package com.normation.plugins.apiauthorizations

import bootstrap.liftweb.AuthBackendProvidersManager
import com.normation.errors.*
import com.normation.plugins.PluginExtensionPoint
import com.normation.plugins.PluginStatus
import com.normation.rudder.domain.appconfig.FeatureSwitch.*
import com.normation.rudder.users.CurrentUser
import com.normation.rudder.users.UserRepository
import com.normation.rudder.web.snippet.UserInformation
import com.normation.zio.UnsafeRun
import net.liftweb.common.Loggable
import net.liftweb.util.Helpers.*
import scala.reflect.ClassTag
import scala.xml.NodeSeq

class UserInformationExtension(
    val status:                  PluginStatus,
    userRepository:              UserRepository,
    authBackendProvidersManager: AuthBackendProvidersManager
)(implicit
    val ttag:                    ClassTag[UserInformation]
) extends PluginExtensionPoint[UserInformation] with Loggable {

  def pluginCompose(snippet: UserInformation): Map[String, NodeSeq => NodeSeq] = Map(
    "userCredentials" -> render
  )

  def render(xml: NodeSeq): NodeSeq = {
    (for {
      user     <- CurrentUser.get.toRight(Inconsistency("Could not find user name to get REST API token feature switch")).toIO
      userInfo <- userRepository.get(user.getUsername).notOptional("Could not get token feature status for unknown user in base")
      provider  = userInfo.managedBy
      status   <- authBackendProvidersManager
                    .getProviderProperties()
                    .get(provider)
                    .map(_.restTokenFeatureSwitch)
                    .notOptional("Could not get token feature status for unknown provider")
    } yield {
      status
    }).runNow match {
      case Enabled  => renderMenu(xml)
      // even if plugin is enabled, if the user REST API token feature is disabled they should not see the menu
      case Disabled => xml
    }
  }

  private def renderMenu(xml: NodeSeq) = {
    /* xml is a menu entry which looks like:
      <li class="user user-menu">
        <a href="#">
          <span>
            <span class="hidden-xs fa fa-user"></span>
            admin
          </span>
        </a>
      </li>
     *
     *
     * We want to do:

     <li class="dropdown user user-menu ">
        <a href="#" class="dropdown-toggle" data-bs-toggle="dropdown">
          <span>
            <span class="hidden-xs fa fa-user"></span>
            admin
          </span>
          <i class="fa fa-angle-down" style="margin-left:15px;"></i>
        </a>
        <ul class="dropdown-menu" role="menu">
        ... here elm app ...
        </ul>
      </li>
     */

    val embedAppXml = {
      <ul id="userApiTokenManagement" class="dropdown-menu">
        <head_merge>
          <link rel="stylesheet" type="text/css" href="/toserve/apiauthorizations/media.css" media="screen" data-lift="with-cached-resource" />
          <script type="text/javascript" data-lift="with-cached-resource"  src="/toserve/apiauthorizations/rudder-userapitoken.js"></script>
        </head_merge>
        <li id="user-token-app">
          <div id="user-token-main">

          </div>
        </li>

        <script data-lift="with-nonce">
        //<![CDATA[
          // init elm app
          $(document).ready(function(){
              var node = document.getElementById("user-token-main");
              var initValues = {
                  contextPath : contextPath
              };
              var app  = Elm.UserApiToken.init({node: node, flags: initValues});

            $('#userApiTokenManagement').on('click',function(e){
              e.preventDefault();
              e.stopPropagation();
              return false;
            });
          });
        // ]]>
        </script>
      </ul>
    }

    (
      "#user-menu [class+]" #> "dropdown notifications-menu"
      & "#user-menu-action [style+]" #> "cursor:pointer"
      & "#user-menu-action [class+]" #> "dropdown-toggle"
      & "#user-menu-action [data-bs-toggle+]" #> "dropdown"
      andThen "#user-menu *+" #> embedAppXml // need to be andThen, else other children mod are erased :/
    ).apply(xml)
  }

}
