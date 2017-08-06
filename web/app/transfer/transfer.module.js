'use strict';

/** Module for controlling and monitoring transfers. */
angular.module('stork.transfer', [
  'stork.transfer.browse', 'stork.transfer.queue', 'stork.credentials'
])

.controller('Transfer', function (
  $rootScope, $scope, user, stork, $modal, endpoints)
{
  // Hardcoded options.
  $scope.optSet = [{
      'title': 'Use transfer optimization',
      'param': 'optimizer',
      'description':
        'Automatically adjust transfer options using the given optimization algorithm.',
      'choices': [ ['None', null], ['2nd Order', '2nd_order'], ['PCP', 'pcp'] ]
    },{
      'title': 'Overwrite existing files',
      'param': 'overwrite',
      'description':
        'By default, destination files with conflicting file names will be overwritten. '+
        'Saying no here will cause the transfer to fail if there are any conflicting files.',
      'choices': [ ['Yes', true], ['No', false] ]
    },{
      'title': 'Verify file integrity',
      'param': 'verify',
      'description':
        'Enable this if you want checksum verification of transferred files.',
      'choices': [ ['Yes', true], ['No', false] ]
    },{
      'title': 'Encrypt data channel',
      'param': 'encrypt',
      'description':
        'Enables data transfer encryption, if supported. This provides additional data security '+
        'at the cost of transfer speed.',
      'choices': [ ['Yes', true], ['No', false] ]
    },{
      'title': 'Compress data channel',
      'param': 'compress',
      'description':
        'Compresses data over the wire. This may improve transfer '+
        'speed if the data is text-based or structured.',
      'choices': [ ['Yes', true], ['No', false] ]
    }
  ];

  $scope.job = {
    src:  endpoints.get('left'),
    dest: endpoints.get('right'),
    options: {
      'optimizer': null,
      'overwrite': true,
      'verify'   : false,
      'encrypt'  : false,
      'compress' : false
    }
  };

  $scope.canTransfer = function (srcName, destName, contents) {
    var src = endpoints.get(srcName);
    var dest = endpoints.get(destName);
    if (!src || !dest || !src.uri || !dest.uri)
      return false;
    if (_.size(src.$selected) < 1 || _.size(dest.$selected) != 1)
      return false;
    if (_.values(src.$selected)[0].dir && !_.values(dest.$selected)[0].dir)
      /*$modal({
      title: 'ATTENTION',
      contentTemplate: 'transfer-error.html',
      });*/
      return false;
    return true;
  };

  $scope.transfer = function (srcName, destName, contents) {
    var src = endpoints.get(srcName);
    var dest = endpoints.get(destName);

    var job = angular.copy($scope.job);
    job.src = src;
    job.dest = dest;
    var su = job.src.uri  = _.keys(src.$selected)[0];
    var du = job.dest.uri = _.keys(dest.$selected)[0];

    // If src is a file and dest a directory, add file name.
    if (src.$selected[su].file && dest.$selected[du].dir) {
      var n = new URI(su).segment(-1);
      job.dest.uri = du = new URI(du).segment(n).toString();
    }

    var modal = $modal({
      title: 'Transfer',
      contentTemplate: 'transfer-modal.html'
    });

    console.log(job);
    modal.$scope.job = job;
    modal.$scope.submit = $scope.submit;
  };

  

  $scope.submit = function (job, then) {
    return stork.submit(job).then(
      function (d) {
        if (then)
          then(d);
        $modal({
          title: 'Success!',
          content: 'Job accepted with ID '+d.job_id
        });
        return d;
      }, function (e) {
        if (then)
          then(e);
        $modal({
          title: 'Failed to submit job',
          content: e
        });
        throw e;
      }
    );
  };
  
/* $scope.mk_dir = function (name) {
   //$modalInstance.close(name);
  };
*/


})
