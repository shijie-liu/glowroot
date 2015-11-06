/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* global glowroot */

glowroot.controller('NavbarCtrl', [
  '$scope',
  '$location',
  'queryStrings',
  function ($scope, $location, queryStrings) {
    $scope.queryString = function (preserveServerGroup, preserveTransactionType) {
      var query = {};
      if (preserveServerGroup) {
        query['server-rollup'] = $location.search()['server-rollup'];
      }
      if (preserveTransactionType) {
        query['transaction-type'] = $location.search()['transaction-type'];
      }
      var last = $location.search().last;
      if (last) {
        query.last = last;
      }
      var from = $location.search().from;
      var to = $location.search().to;
      if (from !== undefined && to !== undefined) {
        query.from = from;
        query.to = to;
      }
      return queryStrings.encodeObject(query);
    };
  }
]);
