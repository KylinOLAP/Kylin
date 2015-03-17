/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

'use strict';


KylinApp.controller('ModelEditCtrl', function ($scope, $q, $routeParams, $location, $templateCache, $interpolate, MessageService, TableService, CubeDescService, CubeService, loadingRequest, SweetAlert,$log,cubeConfig,CubeDescModel,ModelDescService,MetaModel,TableModel) {
    $scope.cubeConfig = cubeConfig;
    //add or edit ?
    var absUrl = $location.absUrl();
    $scope.modelMode = absUrl.indexOf("/models/add")!=-1?'addNewModel':absUrl.indexOf("/models/edit")!=-1?'editExistModel':'default';


    $scope.getPartitonColumns = function(tableName){
        var columns = _.filter($scope.getColumnsByTable(tableName),function(column){
            return column.datatype==="date"||column.datatype==="string";
        });
        return columns;
    };

    $scope.getColumnsByTable = function (tableName) {
        var temp = [];
        angular.forEach(TableModel.selectProjectTables, function (table) {
            if (table.name == tableName) {
                temp = table.columns;
            }
        });
        return temp;
    };

    $scope.getColumnType = function (_column,table){
        var columns = $scope.getColumnsByTable(table);
        var type;
        angular.forEach(columns,function(column){
            if(_column === column.name){
                type = column.datatype;
                return;
            }
        });
        return type;
    };

    // ~ Define data
    $scope.state = {
        "modelSchema": ""
    };

    // ~ init
    if ($scope.isEdit = !!$routeParams.modelName) {
        ModelDescService.get({model_name: $routeParams.modelName}, function (model) {
                    if (model) {
                        $scope.model = model;
                        //use
                        //convert GMT mills ,to make sure partition date show GMT Date
                        //should run only one time
                        if(model.partition_desc&&model.partition_desc.partition_date_start)
                        {
                            MetaModel.converDateToGMT();
                        }
                    }
                });

    } else {
        $scope.model = MetaModel.createNew();;
    }

    // ~ public methods
    $scope.aceChanged = function () {
    };

    $scope.aceLoaded = function(){
    };

    $scope.prepareCube = function () {
        // generate column family

        if ($scope.model.partition_desc.partition_date_column&&($scope.model.partition_desc.partition_date_start|$scope.model.partition_desc.partition_date_start==0)) {
            var dateStart = new Date($scope.model.partition_desc.partition_date_start);
            dateStart = (dateStart.getFullYear() + "-" + (dateStart.getMonth() + 1) + "-" + dateStart.getDate());
            //switch selected time to utc timestamp
            $scope.model.partition_desc.partition_date_start = new Date(moment.utc(dateStart, "YYYY-MM-DD").format()).getTime();


            if($scope.model.partition_desc.partition_date_column.indexOf(".")==-1){
            $scope.model.partition_desc.partition_date_column=$scope.model.fact_table+"."+$scope.model.partition_desc.partition_date_column;
            }

        }
        $scope.state.modelSchema = angular.toJson($scope.model, true);
        $scope.state.project = $scope.projectModel.selectedProject;

    };

    $scope.cubeResultTmpl = function (notification) {
        // Get the static notification template.
        var tmpl = notification.type == 'success' ? 'cubeResultSuccess.html' : 'cubeResultError.html';
        return $interpolate($templateCache.get(tmpl))(notification);
    };

    $scope.saveModel = function (design_form) {

        try {
            angular.fromJson($scope.state.cubeSchema);
        } catch (e) {
            SweetAlert.swal('Oops...', 'Invalid cube json format..', 'error');
            return;
        }

        SweetAlert.swal({
            title: '',
            text: 'Are you sure to save the Model ?',
            type: '',
            showCancelButton: true,
            confirmButtonColor: '#DD6B55',
            confirmButtonText: "Yes",
            closeOnConfirm: true
        }, function(isConfirm) {
            if(isConfirm){
                loadingRequest.show();

                if ($scope.isEdit) {
                    CubeService.update({}, {modelDescData:$scope.state.modelSchema, modelName: $routeParams.modelName, project: $scope.state.project}, function (request) {
                        if (request.successful) {
                            $scope.state.modelSchema = request.modelSchema;
                            MessageService.sendMsg($scope.cubeResultTmpl({'text':'Updated the cube successfully.',type:'success'}), 'success', {}, true, 'top_center');

                            if (design_form) {
                                design_form.$invalid = true;
                            }
                        } else {
                            $scope.saveModelRollBack();
                                var message =request.message;
                                var msg = !!(message) ? message : 'Failed to take action.';
                                MessageService.sendMsg($scope.cubeResultTmpl({'text':msg,'schema':$scope.state.modelSchema}), 'error', {}, true, 'top_center');
                        }
                        //end loading
                        loadingRequest.hide();
                    }, function (e) {
                        $scope.saveModelRollBack();

                        if(e.data&& e.data.exception){
                            var message =e.data.exception;
                            var msg = !!(message) ? message : 'Failed to take action.';
                            MessageService.sendMsg($scope.cubeResultTmpl({'text':msg,'schema':$scope.state.modelSchema}), 'error', {}, true, 'top_center');
                        } else {
                            MessageService.sendMsg($scope.cubeResultTmpl({'text':'Failed to take action.','schema':$scope.state.modelSchema}), 'error', {}, true, 'top_center');
                        }
                        loadingRequest.hide();
                    });
                } else {
                    CubeService.save({}, {cubeDescData: $scope.state.cubeSchema,modelDescData:$scope.state.modelSchema, project: $scope.state.project}, function (request) {
                        if(request.successful) {
                            $scope.state.modelSchema = request.modelSchema;

                            MessageService.sendMsg($scope.cubeResultTmpl({'text':'Created the cube successfully.',type:'success'}), 'success', {}, true, 'top_center');
                        } else {
                            $scope.saveModelRollBack();
                            var message =request.message;
                            var msg = !!(message) ? message : 'Failed to take action.';
                            MessageService.sendMsg($scope.cubeResultTmpl({'text':msg,'schema':$scope.state.modelSchema}), 'error', {}, true, 'top_center');
                        }

                        //end loading
                        loadingRequest.hide();
                    }, function (e) {
                        $scope.saveModelRollBack();

                        if (e.data && e.data.exception) {
                            var message =e.data.exception;
                            var msg = !!(message) ? message : 'Failed to take action.';
                            MessageService.sendMsg($scope.cubeResultTmpl({'text':msg,'schema':$scope.state.modelSchema}), 'error', {}, true, 'top_center');
                        } else {
                            MessageService.sendMsg($scope.cubeResultTmpl({'text':"Failed to take action.",'schema':$scope.state.modelSchema}), 'error', {}, true, 'top_center');
                        }
                        //end loading
                        loadingRequest.hide();

                    });
                }
            }
            else{
                $scope.saveModelRollBack();
            }
        });
    };

//    reverse the date
    $scope.saveModelRollBack = function (){
        if($scope.model&&($scope.model.partition_desc.partition_date_start||$scope.model.partition_desc.partition_date_start==0))
        {
            $scope.model.partition_desc.partition_date_start+=new Date().getTimezoneOffset()*60000;
        }
    };

    $scope.$watch('projectModel.selectedProject', function (newValue, oldValue) {
        if(!newValue){
            return;
        }
        var param = {
            ext: true,
            project:newValue
        };
        if(newValue){
            TableModel.initTables();
            TableService.list(param, function (tables) {
                angular.forEach(tables, function (table) {
                    table.name = table.database+"."+table.name;
                    TableModel.addTable(table);
                });
            });
        }
    });
});