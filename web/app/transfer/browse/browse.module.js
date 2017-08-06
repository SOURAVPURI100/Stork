'use strict';

/** Module for browsing transfer endpoints. */
angular.module('stork.transfer.browse', [
  'ngCookies'
])

.service('history', function (stork) {
  var history = [];
  var listeners = [];

  this.history = history;

  this.fetch = function (uri) {
    stork.history(uri).then(function (h) {
      history = h;
      for (var i = 0; i < listeners.length; i++)
        listeners[i](history);
    });
  };

  this.fetch();

  /** Add a callback for when history changes. */
  this.listen = function (f) {
    if (!angular.isFunction(f))
      return;
    listeners.push(f);
    f(history);
  };
})

/** A place to store browse URIs to persist across pages. */
.service('endpoints', function ($cookieStore) {
  var ends = {};

  /** Get an endpoint by name. */
  this.get = function (name) {
    return ends[name] || (ends[name] = {});
  };
})

.controller('History', function (history) {
  var me = this;
  this.list = [];
  history.listen(function (history) {
    me.list = history;
  });
})

.controller('Browse', function (
  $scope, $q, $modal, $window, $attrs,
  stork, user, history, endpoints, $location)
{
  // Restore a saved endpoint. side should already be in scope.
  $scope.end = endpoints.get($attrs.side);

  // Reset (or initialize) the browse pane.
  $scope.reset = function () {
    $scope.uri = {};
    $scope.state = {disconnected: true};
    delete $scope.error;
    delete $scope.root;
  };

  $scope.reset();

  // Example display
  $scope.exDisplay = function (uri) {
    uri = new URI(uri).normalize();
    var readable = uri.readable();
    $scope.uri.text = readable;
    if ($scope.history)
      $scope.history.show = false;
    delete $scope.root;
    delete $scope.error;
    delete $scope.state.disconnected;
  };

  // Set the endpoint URI.
  $scope.go = function (uri) {
    if (!uri)
      return $scope.reset();
    if (typeof uri === 'string')
      uri = new URI(uri).normalize();
    else
      uri = uri.clone().normalize();

    var readable = uri.readable();

    // Make sure the input box matches the URI we're dealing with.
    if (readable != $scope.uri.text) {
      $scope.uri.text = readable;
      uri = new URI(readable).normalize();
    }

    $scope.uri.parsed = uri;
    $scope.uri.text = readable;
    if ($scope.uri.state)
      $scope.uri.state.changed = false;
    else
      $scope.uri.state = {};
    $scope.end.uri = readable;

    // Clean up after previous calls.
    if ($scope.history)
      $scope.history.show = false;
    delete $scope.root;
    delete $scope.state.disconnected;
    $scope.state.loading = true;

    history.fetch(readable);

    $scope.fetch(uri).then(function (list) {
      delete $scope.state.loading;
      $scope.root = list;
      $scope.root.name = readable;
      $scope.open = true;
      $scope.unselectAll();
    }, function (error) {
      delete $scope.state.loading;
      $scope.error = error;
    });
  };

  /* Reload the existing listing. */
  $scope.refresh = function () {
    $scope.go($scope.uri.parsed);
  };

  /* Fetch and cache the listing for the given URI. */
  $scope.fetch = function (uri) {
    var scope = this;
    scope.loading = true;
    delete scope.error;

    var ep = angular.copy($scope.end);
    ep.uri = uri.href();

    return stork.ls(ep, 1).then(
      function (d) {
        if (scope.root)
          d.name = scope.root.name || d.name;
        return scope.root = d;
      }, function (e) {
        return $q.reject(scope.error = e);
      }
    ).finally(function () {
      scope.loading = false;
    });
  };

  // Get the path URI of this item.
  $scope.path = function () {
    if ($scope.root === this.root)
      return $scope.uri.parsed.clone();
    return this.parent().path().segmentCoded(this.root.name);
  };

  // Open the mkdir dialog.
  $scope.mkdir = function () {
    var modal = $modal({
      title: 'Create Directory',
      contentTemplate: 'new-folder.html',
      //controller : 'Transfer',
      scope: $scope
    });

    modal.$scope.uri.parsed = $scope.uri.parsed;

    /*modalInstance.result.then(function (pn) {
      var u = new URI(pn[0]).segment(pn[1]);
      return stork.mkdir(u.href()).then(
        function (m) {
          $scope.refresh();
        }, function (e) {
          alert('Could not create folder: '+e.error);
        }
      );
    });*/
  };

  $scope.mk_dir = function (name) {
    var u = $scope.uri.parsed;
    u = u._string+name+"/";
    //u = new URI(u);
    if (!u) return;
    //u.segment(name);
    return stork.mkdir(u).then(
      function (m) {
        $scope.refresh();
      }, function (e) {
           alert('Could not create folder: '+e.error);
      }
   );
 };


  /* Open cred modal. */
  $scope.credModal = function () {
    $modal({
      title: 'Select Credential',
      contentTemplate: 'select-credential.html',
      scope: $scope
    });
  };

  // Delete the selected files.
  $scope.rm = function (uris) {
    _.each(uris, function (u) {
      if (confirm("Delete "+u+"?")) {
        return stork.delete(u).then(
          function () {
            $scope.refresh();
          }, function (e) {
            alert('Could not delete file: '+e.error);
          }
        );
      }
    });
  };

  // Download the selected file.
  $scope.download = function (uris) {
    if (uris == undefined || uris.length == 0)
      return alert('You must select a file.');
    else if (uris.length > 1)
      return alert('You can only download one file at a time.');

    var end = {
      uri: uris[0],
      credential: $scope.end.credential
    };
    stork.get(end);
  };

  // Share the selected file.
  $scope.share = function (uris) {
    if (uris == undefined || uris.length == 0)
      return alert('You must select a file.');
    else if (uris.length > 1)
      return alert('You must select exactly one file.');
    stork.share({
      uri: uris[0],
      credential: $scope.end.credential
    }).then(function (r) {
      var link = "https://storkcloud.org/api/stork/get?uuid="+r.uuid;
      var scope = $scope.$new();
      scope.link = link;
      $modal({
        title: 'Share link',
        contentTemplate: 'show-share-link.html',
        scope: angular.extend(scope)
      });
    }, function (e) {
      alert('Could not create share: '+e.error);
    });
  };

  // Return the scope corresponding to the parent directory.
  $scope.parent = function () {
    if (this !== $scope) {
      if (this.$parent.root !== this.root)
        return this.$parent;
      return this.$parent.parent();
    }
  };

  $scope.up_dir = function () {
    var u = $scope.uri.parsed;
    if (!u) return;
    u = u.clone().filename('../').normalize();
    $scope.go(u);
  };

  $scope.toggle = function () {
    var scope = this;
    var root = scope.root;
    if (root && root.dir && (scope.open = !scope.open) && !root.files) {
      scope.fetch(scope.path());
    }
  };

  $scope.select = function (e) {
    var scope = this;
    var u = this.path();
      // Enable to choose mutiple files.
    if (e.ctrlKey) {
      this.root.selected = !this.root.selected;
      if (this.root.selected)
        $scope.end.$selected[u] = this.root;
      else
        delete $scope.end.$selected[u];
    } else if ($scope.selectedUris().length != 1) {
      // Either nothing is selected, or multiple things are selected.
      $scope.unselectAll();
      this.root.selected = true;
      $scope.end.$selected[u] = this.root;
    } else if ($scope.selectedUris().length = 1){
      // Only one thing is selected.
      var selected = this.root.selected;
      $scope.unselectAll();
      if (!selected) {
        this.root.selected = true;
        $scope.end.$selected[u] = this.root;
      }
    }

    // Unselect text.
    if (document.selection && document.selection.empty)
      document.selection.empty();
    else if (window.getSelection)
      window.getSelection().removeAllRanges();
  };

  $scope.dragAndDrop = function (e) {
    var scope = this;
    var u = this.path();
    $scope.end.$selected[u] = this.root;
    if (document.selection && document.selection.empty)
      document.selection.empty();
    else if (window.getSelection)
      window.getSelection().removeAllRanges();
  };

  $scope.unselectAll = function () {
    var s = $scope.end.$selected;
    if (s) _.each(s, function (f) {
      delete f.selected;
    });
    $scope.end.$selected = {};
  };

  $scope.selectedUris = function () {
    if (!$scope.end.$selected)
      return [];
    return _.keys($scope.end.$selected);
  };

  /* Supported protocol to show in the dropdown box.ex.ftp://ftp.mozilla.org/,gsiftp://oasis-dm.sdsc.xsede.org/ */
  $scope.dropdownDbx = [
    ["fa-dropbox", "Dropbox", "dropbox://"],
  ];

  $scope.dropdownList = [
    ["fa-globe", "FTP", "ftp:"], 
    ["fa-globe", "SDSC Gordon (GridFTP)", "gsiftp:"],
    ["fa-globe", "HTTP", "http:"],
    ["fa-globe", "SCP","scp:"],
  ];
  /** Default examples to show in the dropdown box. */
  $scope.dropdownExamples = [
    ["fa-search","ftp://ftp.mozilla.org/"],
    ["fa-search","gsiftp://oasis-dm.sdsc.xsede.org/"],
    ["fa-search","http://google.com/"]
  ];

  $scope.openOAuth = function (url) {
    $window.oAuthCallback = function (uuid) {
      $scope.end.credential = {uuid: uuid};
      $scope.refresh();
      $scope.$apply();
    };
    //open a new window to direct to the "url"; syntax: $window.open(url, windowName)
    var child = $window.open(url, 'oAuthWindow');
    return false;
  };

  if ($scope.end.uri)
    $scope.go($scope.end.uri);

  $scope.storkDragStart = function (e) {
    /** or e.target.style.opacity = '.8';*/
    this.style.opacity='.8';
    e.dataTransfer.setData('text', e.target.root);
    ;
  };
  $scope.storkDragEnd = function (e) {
    this.style.opacity='1';
  };
  $scope.storkDragOver = function (e) {
    e.preventDefault();   
  };
  $scope.storkDragEnter = function (e) {
    e.target.style.opacity=".3";
  };
  $scope.storkDragLeave = function (e) {
    e.target.style.opacity="";
  };
  $scope.storkDrop = function (e) {
    e.preventDefault();
    e.target.style.opacity="";    
    if($scope.end == endpoints.get('right') && $scope.canTransfer('left','right',false))
    $scope.transfer('left','right',false);
    else if($scope.end == endpoints.get('left') && $scope.canTransfer('right','left',false))
    $scope.transfer('right','left',false);
    $scope.unselectAll();
  };
   
});

