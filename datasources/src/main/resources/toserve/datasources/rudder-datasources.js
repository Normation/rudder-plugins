/*
*************************************************************************************
* Copyright 2017 Normation SAS
*************************************************************************************
*
* This file is part of Rudder.
*
* Rudder is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU General Public License version 3, the copyright holders add
* the following Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU General
* Public License version 3, when you create a Related Module, this
* Related Module is not considered as a part of the work and may be
* distributed under the license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* Rudder is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Rudder.  If not, see <http://www.gnu.org/licenses/>.

*
*************************************************************************************
*/

var app = angular.module("datasource", []);
var datasourceUrl = "/secure/api/datasources";

app.directive('focusOn', function() {
  return function(scope, elem, attr) {
    scope.$on('focusOn', function(e, name) {
      if(name === attr.focusOn) {
        elem[0].focus();
      }
    });
  };
});
app.factory('focus', function ($rootScope, $timeout) {
  return function(name) {
    $timeout(function (){
      $rootScope.$broadcast('focusOn', name);
    });
  }
});

app.controller("datasourceCtrl", ['$scope', '$timeout', 'orderByFilter','$http', function($scope,$timeout,orderBy,$http,focus) {
  $scope.forms = {};
  $scope.datasources = [];
  // Selected data source
  $scope.selectedDatasource;
  $scope.treeId = "#datasources-tree";
  /* Get data sources */
  $scope.getDataSources = function(){
    return $http.get(contextPath + datasourceUrl).then(function(response){
      var res = response.data.data.datasources;
      $scope.propertyName = 'name';
      $scope.reverse = false;
      $scope.datasources =  orderBy(res, $scope.propertyName, $scope.reverse);
      for(var i=0 ; i<$scope.datasources.length ; i++){
        $scope.datasources[i].newHeader = {"name":"","value":""};
        $scope.datasources[i].newParam  = {"name":"","value":""};
        $scope.datasources[i].modifiedTimes = {
          'schedule'       : convertFromSecond($scope.datasources[i].runParameters.schedule.duration)
        , 'updateTimeout'  : convertFromSecond($scope.datasources[i].updateTimeout)
        , 'requestTimeout' : convertFromSecond($scope.datasources[i].type.parameters.requestTimeout)
        };
        //This will need to be changed when Bug #10554 will be fixed

        //we always to display a string for the default "onMissing" value (if exists). The backend will
        //manage the correct parsing.
        if(typeof $scope.datasources[i].type.parameters.onMissing.value === 'object') {
          $scope.datasources[i].type.parameters.onMissing.value = JSON.stringify($scope.datasources[i].type.parameters.onMissing.value)
        }
      }
    });
  }
  $scope.getDataSources();

  $scope.getDatasource = function(id, index){
    for(var i=0; i<$scope.datasources.length ; i++){
      if($scope.datasources[i].id==id){
        if(index){
          return i;
        }else{
          return $scope.datasources[i];
        }
      }
    }
    return false;
  }
  // DATASOURCES
  $scope.createNewDatasource = function(){
    if($scope.forms.datasourceForm){
      $scope.forms.datasourceForm.$setPristine();
    }
	  $scope.selectedDatasource = {
      "name"        : "",
      "id"          : "",
      "description" : "",
      "type"        :{
        "name"       :"HTTP",
        "parameters" :{
          "checkSsl":true,
          "headers" :[],
          "params" :[],
          "path"    :"",
          "requestTimeout":30,
          "requestMethod":"GET",
          "requestMode":{"name":"byNode"},
          "url"     :"",
          "onMissing": { "name":"delete"},
          "maxParallelReq": 10
        }
  	  },
  	  "runParameters":{
  	    "onGeneration" :false,
  	    "onNewNode"    :false,
  	    "schedule":{
  	      "type":"scheduled",
  	      "duration":21600
  	    }
  	  },
  	  "updateTimeout":30,
  	  "enabled":false,
  	  "newHeader":{"name":"","value":""},
  	  "modifiedTimes":{
  	    "schedule"      : {"second":0 ,"minute":0, "hour": 6},
  	    "updateTimeout" : {"second":30,"minute":0, "hour": 0},
  	    "requestTimeout": {"second":30,"minute":0, "hour": 0}
  	  },
  	  "isNew":true
  	};
  }

  $scope.updateKeyName = function(str){
    if($scope.selectedDatasource.isNew){
      if(str){
        var r = str.replace(/[^\-_a-zA-Z0-9]/g,"_");
        $scope.selectedDatasource.id = r;
      }else{
        $scope.selectedDatasource.id = "";
      }
    }
  }

  $scope.toggleEnabled = function(){
    $scope.selectedDatasource.enabled = !$scope.selectedDatasource.enabled;
    var temp = jQuery.extend(true, {}, $scope.getDatasource($scope.selectedDatasource.id));
    temp.enabled = $scope.selectedDatasource.enabled;
    $http.post(contextPath + datasourceUrl + '/' + temp.id, temp).then(function(response){
      var res = response;
      var index = $scope.getDatasource($scope.selectedDatasource.id, true);
      $scope.datasources[index] = jQuery.extend(true, {}, $scope.selectedDatasource);
    });
  }
  $scope.saveDatasource = function(){
    //CONVERT TIMES
    $scope.selectedDatasource.runParameters.schedule.duration = convertToSecond($scope.selectedDatasource.modifiedTimes.schedule);
    $scope.selectedDatasource.updateTimeout = convertToSecond($scope.selectedDatasource.modifiedTimes.updateTimeout);
    $scope.selectedDatasource.type.parameters.requestTimeout = convertToSecond($scope.selectedDatasource.modifiedTimes.requestTimeout);
    if($scope.selectedDatasource.isNew){
      $http.put(contextPath + datasourceUrl, $scope.selectedDatasource).then(function(response){
        var res = response;
        delete $scope.selectedDatasource.isNew;
        $scope.datasources.push($scope.selectedDatasource);
        createSuccessNotification("The data source '" + $scope.selectedDatasource.name + "' has been successfully created")
      });
    }else{
      $http.post(contextPath + datasourceUrl + '/' + $scope.selectedDatasource.id, $scope.selectedDatasource).then(function(response){
        var res = response;
        var index = $scope.getDatasource($scope.selectedDatasource.id, true);
        $scope.datasources[index] = jQuery.extend(true, {}, $scope.selectedDatasource);
        createSuccessNotification()
      });
    }
  }
  $scope.selectDatasource = function(id){
    if($scope.forms.datasourceForm){
      $scope.forms.datasourceForm.$setPristine();
      $('.well').css('display','none');
      $('.show-details .form-group .fa-chevron-right').removeClass('fa-rotate-90');
    }
    var getDatasource = $scope.getDatasource(id);
    if(getDatasource){
      $scope.selectedDatasource = jQuery.extend(true, {}, getDatasource);
    }
  }
  $scope.deleteDatasource = function(){
    $('#deleteModal').bsModal('show');
  }
  $scope.confirmDeleteDatasource = function(){
    $http.delete(contextPath + datasourceUrl + '/' + $scope.selectedDatasource.id).then(function(response){
      $('#deleteModal').bsModal('hide');
      var index = $scope.getDatasource($scope.selectedDatasource.id, true);
      $scope.datasources.splice(index, 1);
      $scope.selectedDatasource = null;
    });
  }
  // HEADERS
  $scope.resetNewObj = function(obj){
    obj.name   = "";
    obj.value = "";
  }
  $scope.toggleHeaders = function(idHeader, event){
    $('#'+idHeader).toggle(80);
    $(event.currentTarget).find('.fa').toggleClass('fa-rotate-90');
  }
  $scope.toggleExample = function(event){
    $(event.currentTarget).parent().find('.example-help').toggle();
    $(event.currentTarget).toggleClass('show');
  }
  $scope.addNewObj = function(array, newObj){
    var exists = false;
    for(var i=0; i<array.length; i++){
      if(array[i].name.toLowerCase() == newObj.name.toLowerCase()){
        exists = true;
      }
    }
    if(!exists){
      array.push(angular.copy(newObj));
      $scope.resetNewObj(newObj);
    }
  }
  $scope.removeObj = function(array,index){
    array.splice(index, 1);
  }
  $scope.hasObj = function(obj){
    return Object.keys(obj).length;
  }

  $scope.toggleInfo = function(event, action){
    $(event.currentTarget).bsPopover(action);
  }
  $scope.toggleWell = function(event){
    var el = $(event.currentTarget);
    el.find('.fa').toggleClass('fa-rotate-90');
    el.parent().parent().find('.well').toggle();
  }
  adjustHeight($scope.treeId);

}]);

function convertFromSecond(time) {
  var hour = Math.floor(time/3600);
  var min = Math.floor((time-hour*3600)/60);
  var sec = time - min*60 - hour*3600;

  return {
      "second"   : sec
    , "minute"   : min
    , "hour"     : hour
  }
}
function convertToSecond(time) {
  var s = 0;
  if(time.second !== undefined) { s = time.second }
  var m = 0;
  if(time.minute !== undefined) { m = time.minute }
  var h = 0;
  if(time.hour !== undefined) { h = time.hour}
  var seconds = s + m*60 + h*3600
  return seconds;
}
function objToArray(obj){
  var arr = [];
  for(x in obj){
    var temp = {'name':x, 'value':obj[x]};
    arr.push(temp);
  }
  return arr;
}

$(document).ready(function(){
  angular.bootstrap('#datasource', ['datasource']);
});

