/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* global define, module, require, exports */

(function (root, factory) {
    if (typeof define === 'function' && define.amd) {
        define(['jquery',
                'nf.Common',
                'nf.Dialog',
                'nf.CanvasUtils',
                'nf.ContextMenu',
                'nf.ClusterSummary',
                'nf.ErrorHandler',
                'nf.Settings'],
            function ($, nfCommon, nfDialog, nfCanvasUtils, nfContextMenu, nfClusterSummary, nfErrorHandler, nfSettings) {
                return (nf.ng.Canvas.FlowStatusCtrl = factory($, nfCommon, nfDialog, nfCanvasUtils, nfContextMenu, nfClusterSummary, nfErrorHandler, nfSettings));
            });
    } else if (typeof exports === 'object' && typeof module === 'object') {
        module.exports = (nf.ng.Canvas.FlowStatusCtrl =
            factory(require('jquery'),
                require('nf.Common'),
                require('nf.Dialog'),
                require('nf.CanvasUtils'),
                require('nf.ContextMenu'),
                require('nf.ClusterSummary'),
                require('nf.ErrorHandler'),
                require('nf.Settings')));
    } else {
        nf.ng.Canvas.FlowStatusCtrl = factory(root.$,
            root.nf.Common,
            root.nf.Dialog,
            root.nf.CanvasUtils,
            root.nf.ContextMenu,
            root.nf.ClusterSummary,
            root.nf.ErrorHandler,
            root.nf.Settings);
    }
}(this, function ($, nfCommon, nfDialog, nfCanvasUtils, nfContextMenu, nfClusterSummary, nfErrorHandler, nfSettings) {
    'use strict';

    return function (serviceProvider) {
        'use strict';

        var config = {
            search: 'Search',
            urls: {
                search: '../nifi-api/flow/search-results',
                status: '../nifi-api/flow/status'
            }
        };

        function FlowStatusCtrl() {
            this.connectedNodesCount = "-";
            this.activeThreadCount = "-";
            this.totalQueued = "-";
            this.controllerTransmittingCount = "-";
            this.controllerNotTransmittingCount = "-";
            this.controllerRunningCount = "-";
            this.controllerStoppedCount = "-";
            this.controllerInvalidCount = "-";
            this.controllerDisabledCount = "-";
            this.controllerUpToDateCount = "-";
            this.controllerLocallyModifiedCount = "-";
            this.controllerStaleCount = "-";
            this.controllerLocallyModifiedAndStaleCount = "-";
            this.controllerSyncFailureCount = "-";
            this.statsLastRefreshed = "-";

            /**
             * The search controller.
             */
            this.search = {

                /**
                 * Get the search input element.
                 */
                getInputElement: function () {
                    return $('#search-field');
                },

                /**
                 * Get the search button element.
                 */
                getButtonElement: function () {
                    return $('#search-button');
                },

                /**
                 * Get the search container element.
                 */
                getSearchContainerElement: function () {
                    return $('#search-container');
                },

                /**
                 * Initialize the search controller.
                 */
                init: function () {

                    var searchCtrl = this;

                    // Create new jQuery UI widget
                    $.widget('nf.searchAutocomplete', $.ui.autocomplete, {
                        reset: function () {
                            this.term = null;
                        },
                        _create: function () {
                            this._super();
                            this.widget().menu('option', 'items', '> :not(.search-header, .search-no-matches)');
                        },
                        _resizeMenu: function () {
                            var ul = this.menu.element;
                            ul.width(400);
                        },
                        _normalize: function (searchResults) {
                            var items = [];
                            items.push(searchResults);
                            return items;
                        },
                        _renderMenu: function (ul, items) {
                            var nfSearchAutocomplete = this;

                            // the object that holds the search results is normalized into a single element array
                            var searchResults = items[0];

                            // show all processors
                            if (!nfCommon.isEmpty(searchResults.processorResults)) {
                                ul.append('<li class="search-header"><div class="search-result-icon icon icon-processor"></div>Processors</li>');
                                $.each(searchResults.processorResults, function (i, processorMatch) {
                                    nfSearchAutocomplete._renderItem(ul, processorMatch);
                                });
                            }

                            // show all process groups
                            if (!nfCommon.isEmpty(searchResults.processGroupResults)) {
                                ul.append('<li class="search-header"><div class="search-result-icon icon icon-group"></div>Process Groups</li>');
                                $.each(searchResults.processGroupResults, function (i, processGroupMatch) {
                                    nfSearchAutocomplete._renderItem(ul, processGroupMatch);
                                });
                            }

                            // show all remote process groups
                            if (!nfCommon.isEmpty(searchResults.remoteProcessGroupResults)) {
                                ul.append('<li class="search-header"><div class="search-result-icon icon icon-group-remote"></div>Remote Process Groups</li>');
                                $.each(searchResults.remoteProcessGroupResults, function (i, remoteProcessGroupMatch) {
                                    nfSearchAutocomplete._renderItem(ul, remoteProcessGroupMatch);
                                });
                            }

                            // show all connections
                            if (!nfCommon.isEmpty(searchResults.connectionResults)) {
                                ul.append('<li class="search-header"><div class="search-result-icon icon icon-connect"></div>Connections</li>');
                                $.each(searchResults.connectionResults, function (i, connectionMatch) {
                                    nfSearchAutocomplete._renderItem(ul, connectionMatch);
                                });
                            }

                            // show all input ports
                            if (!nfCommon.isEmpty(searchResults.inputPortResults)) {
                                ul.append('<li class="search-header"><div class="search-result-icon icon icon-port-in"></div>Input Ports</li>');
                                $.each(searchResults.inputPortResults, function (i, inputPortMatch) {
                                    nfSearchAutocomplete._renderItem(ul, inputPortMatch);
                                });
                            }

                            // show all output ports
                            if (!nfCommon.isEmpty(searchResults.outputPortResults)) {
                                ul.append('<li class="search-header"><div class="search-result-icon icon icon-port-out"></div>Output Ports</li>');
                                $.each(searchResults.outputPortResults, function (i, outputPortMatch) {
                                    nfSearchAutocomplete._renderItem(ul, outputPortMatch);
                                });
                            }

                            // show all funnels
                            if (!nfCommon.isEmpty(searchResults.funnelResults)) {
                                ul.append('<li class="search-header"><div class="search-result-icon icon icon-funnel"></div>Funnels</li>');
                                $.each(searchResults.funnelResults, function (i, funnelMatch) {
                                    nfSearchAutocomplete._renderItem(ul, funnelMatch);
                                });
                            }

                            // ensure there were some results
                            if (ul.children().length === 0) {
                                ul.append('<li class="unset search-no-matches">No results matched the search terms</li>');
                            }
                        },
                        _renderItem: function (ul, match) {
                            var itemContent = $('<a></a>').append($('<div class="search-match-header"></div>').text(match.name));
                            $.each(match.matches, function (i, match) {
                                itemContent.append($('<div class="search-match"></div>').text(match));
                            });
                            return $('<li></li>').data('ui-autocomplete-item', match).append(itemContent).appendTo(ul);
                        }
                    });

                    // configure the new searchAutocomplete jQuery UI widget
                    this.getInputElement().searchAutocomplete({
                        appendTo: '#search-flow-results',
                        position: {
                            my: 'right top',
                            at: 'right bottom',
                            offset: '1 1'
                        },
                        source: function (request, response) {
                            // create the search request
                            $.ajax({
                                type: 'GET',
                                data: {
                                    q: request.term
                                },
                                dataType: 'json',
                                url: config.urls.search
                            }).done(function (searchResponse) {
                                response(searchResponse.searchResultsDTO);
                            });
                        },
                        select: function (event, ui) {
                            var item = ui.item;

                            // show the selected component
                            nfCanvasUtils.showComponent(item.groupId, item.id);

                            searchCtrl.getInputElement().val('').blur();

                            // stop event propagation
                            return false;
                        },
                        open: function (event, ui) {
                            // show the glass pane
                            var searchField = $(this);
                            $('<div class="search-glass-pane"></div>').one('click', function () {
                            }).appendTo('body');
                        },
                        close: function (event, ui) {
                            // set the input text to '' and reset the cached term
                            $(this).searchAutocomplete('reset');
                            searchCtrl.getInputElement().val('');

                            // remove the glass pane
                            $('div.search-glass-pane').remove();
                        }
                    });

                    // hide the search input
                    searchCtrl.toggleSearchField();
                },

                /**
                 * Toggle/Slide the search field open/closed.
                 */
                toggleSearchField: function () {
                    var searchCtrl = this;

                    // hide the context menu if necessary
                    nfContextMenu.hide();

                    var isVisible = searchCtrl.getInputElement().is(':visible');
                    var display = 'none';
                    var class1 = 'search-container-opened';
                    var class2 = 'search-container-closed';
                    if (!isVisible) {
                        searchCtrl.getButtonElement().css('background-color', '#FFFFFF');
                        display = 'inline-block';
                        class1 = 'search-container-closed';
                        class2 = 'search-container-opened';
                    } else {
                        searchCtrl.getInputElement().css('display', display);
                    }

                    this.getSearchContainerElement().switchClass(class1, class2, 500, function () {
                        searchCtrl.getInputElement().css('display', display);
                        if (!isVisible) {
                            searchCtrl.getButtonElement().css('background-color', '#FFFFFF');
                            searchCtrl.getInputElement().focus();
                        } else {
                            searchCtrl.getButtonElement().css('background-color', '#E3E8EB');
                        }
                    });
                }
            }

            /**
             * The bulletins controller.
             */
            this.bulletins = {

                /**
                 * Update the bulletins.
                 *
                 * @param response  The controller bulletins returned from the `../nifi-api/controller/bulletins` endpoint.
                 */
                update: function (response) {

                    // icon for system bulletins
                    var bulletinIcon = $('#bulletin-button');
                    var currentBulletins = bulletinIcon.data('bulletins');

                    // update the bulletins if necessary
                    if (nfCommon.doBulletinsDiffer(currentBulletins, response.bulletins)) {
                        bulletinIcon.data('bulletins', response.bulletins);

                        // get the formatted the bulletins
                        var bulletins = nfCommon.getFormattedBulletins(response.bulletins);

                        // bulletins for this processor are now gone
                        if (bulletins.length === 0) {
                            if (bulletinIcon.data('qtip')) {
                                bulletinIcon.removeClass('has-bulletins').qtip('api').destroy(true);
                            }
                        } else {
                            var newBulletins = nfCommon.formatUnorderedList(bulletins);

                            // different bulletins, refresh
                            if (bulletinIcon.data('qtip')) {
                                bulletinIcon.qtip('option', 'content.text', newBulletins);
                            } else {
                                // no bulletins before, show icon and tips
                                bulletinIcon.addClass('has-bulletins').qtip($.extend({},
                                    nfCanvasUtils.config.systemTooltipConfig,
                                    {
                                        content: newBulletins,
                                        position: {
                                            at: 'bottom left',
                                            my: 'top right',
                                            adjust: {
                                                x: 4
                                            }
                                        }
                                    }
                                ));
                            }
                        }
                    }

                    // update controller service and reporting task bulletins
                    nfSettings.setBulletins(response.controllerServiceBulletins, response.reportingTaskBulletins);
                }

            }
        }

        FlowStatusCtrl.prototype = {
            constructor: FlowStatusCtrl,

            /**
             * Initialize the flow status controller.
             */
            init: function () {
                this.search.init();
            },

            /**
             * Reloads the current status of the flow.
             */
            reloadFlowStatus: function () {
                var flowStatusCtrl = this;

                return $.ajax({
                    type: 'GET',
                    url: config.urls.status,
                    dataType: 'json'
                }).done(function (response) {
                    // report the updated status
                    if (nfCommon.isDefinedAndNotNull(response.controllerStatus)) {
                        flowStatusCtrl.update(response.controllerStatus);
                    }
                }).fail(nfErrorHandler.handleAjaxError);
            },

            /**
             * Updates the cluster summary.
             *
             * @param summary
             */
            updateClusterSummary: function (summary) {
                // see if this node has been (dis)connected
                if (nfClusterSummary.isConnectedToCluster() !== summary.connectedToCluster) {
                    if (summary.connectedToCluster) {
                        nfDialog.showConnectedToClusterMessage();
                    } else {
                        nfDialog.showDisconnectedFromClusterMessage();
                    }
                }

                var color = '#728E9B';

                // update the connection state
                if (summary.connectedToCluster) {
                    if (nfCommon.isDefinedAndNotNull(summary.connectedNodes)) {
                        var connectedNodes = summary.connectedNodes.split(' / ');
                        if (connectedNodes.length === 2 && connectedNodes[0] !== connectedNodes[1]) {
                            this.clusterConnectionWarning = true;
                            color = '#BA554A';
                        }
                    }
                    this.connectedNodesCount =
                        nfCommon.isDefinedAndNotNull(summary.connectedNodes) ? summary.connectedNodes : '-';
                } else {
                    this.connectedNodesCount = 'Disconnected';
                    color = '#BA554A';
                }

                // update the color
                $('#connected-nodes-count').closest('div.fa-cubes').css('color', color);
            },

            /**
             * Update the flow status counts.
             *
             * @param status  The controller status returned from the `../nifi-api/flow/status` endpoint.
             */
            update: function (status) {
                var controllerInvalidCount = (nfCommon.isDefinedAndNotNull(status.invalidCount)) ? status.invalidCount : 0;

                if (this.controllerInvalidCount > 0) {
                    $('#controller-invalid-count').parent().removeClass('zero').addClass('invalid');
                } else {
                    $('#controller-invalid-count').parent().removeClass('invalid').addClass('zero');
                }

                // update the report values
                this.activeThreadCount = status.activeThreadCount;

                if (this.activeThreadCount > 0) {
                    $('#flow-status-container').find('.icon-threads').removeClass('zero');
                } else {
                    $('#flow-status-container').find('.icon-threads').addClass('zero');
                }

                this.totalQueued = status.queued;

                if (this.totalQueued.indexOf('0 / 0') >= 0) {
                    $('#flow-status-container').find('.fa-list').addClass('zero');
                } else {
                    $('#flow-status-container').find('.fa-list').removeClass('zero');
                }

                // update the component counts
                this.controllerTransmittingCount =
                    nfCommon.isDefinedAndNotNull(status.activeRemotePortCount) ?
                        status.activeRemotePortCount : '-';

                if (this.controllerTransmittingCount > 0) {
                    $('#flow-status-container').find('.fa-bullseye').removeClass('zero').addClass('transmitting');
                } else {
                    $('#flow-status-container').find('.fa-bullseye').removeClass('transmitting').addClass('zero');
                }

                this.controllerNotTransmittingCount =
                    nfCommon.isDefinedAndNotNull(status.inactiveRemotePortCount) ?
                        status.inactiveRemotePortCount : '-';

                if (this.controllerNotTransmittingCount > 0) {
                    $('#flow-status-container').find('.icon-transmit-false').removeClass('zero').addClass('not-transmitting');
                } else {
                    $('#flow-status-container').find('.icon-transmit-false').removeClass('not-transmitting').addClass('zero');
                }

                this.controllerRunningCount =
                    nfCommon.isDefinedAndNotNull(status.runningCount) ? status.runningCount : '-';

                if (this.controllerRunningCount > 0) {
                    $('#flow-status-container').find('.fa-play').removeClass('zero').addClass('running');
                } else {
                    $('#flow-status-container').find('.fa-play').removeClass('running').addClass('zero');
                }

                this.controllerStoppedCount =
                    nfCommon.isDefinedAndNotNull(status.stoppedCount) ? status.stoppedCount : '-';

                if (this.controllerStoppedCount > 0) {
                    $('#flow-status-container').find('.fa-stop').removeClass('zero').addClass('stopped');
                } else {
                    $('#flow-status-container').find('.fa-stop').removeClass('stopped').addClass('zero');
                }

                this.controllerInvalidCount =
                    nfCommon.isDefinedAndNotNull(status.invalidCount) ? status.invalidCount : '-';

                if (this.controllerInvalidCount > 0) {
                    $('#flow-status-container').find('.fa-warning').removeClass('zero').addClass('invalid');
                } else {
                    $('#flow-status-container').find('.fa-warning').removeClass('invalid').addClass('zero');
                }

                this.controllerDisabledCount =
                    nfCommon.isDefinedAndNotNull(status.disabledCount) ? status.disabledCount : '-';

                if (this.controllerDisabledCount > 0) {
                    $('#flow-status-container').find('.icon-enable-false').removeClass('zero').addClass('disabled');
                } else {
                    $('#flow-status-container').find('.icon-enable-false').removeClass('disabled').addClass('zero');
                }

                this.controllerUpToDateCount =
                    nfCommon.isDefinedAndNotNull(status.upToDateCount) ? status.upToDateCount : '-';

                if (this.controllerUpToDateCount > 0) {
                    $('#flow-status-container').find('.fa-check').removeClass('zero').addClass('up-to-date');
                } else {
                    $('#flow-status-container').find('.fa-check').removeClass('up-to-date').addClass('zero');
                }

                this.controllerLocallyModifiedCount =
                    nfCommon.isDefinedAndNotNull(status.locallyModifiedCount) ? status.locallyModifiedCount : '-';

                if (this.controllerLocallyModifiedCount > 0) {
                    $('#flow-status-container').find('.fa-asterisk').removeClass('zero').addClass('locally-modified');
                } else {
                    $('#flow-status-container').find('.fa-asterisk').removeClass('locally-modified').addClass('zero');
                }

                this.controllerStaleCount =
                    nfCommon.isDefinedAndNotNull(status.staleCount) ? status.staleCount : '-';

                if (this.controllerStaleCount > 0) {
                    $('#flow-status-container').find('.fa-arrow-circle-up').removeClass('zero').addClass('stale');
                } else {
                    $('#flow-status-container').find('.fa-arrow-circle-up').removeClass('stale').addClass('zero');
                }

                this.controllerLocallyModifiedAndStaleCount =
                    nfCommon.isDefinedAndNotNull(status.locallyModifiedAndStaleCount) ? status.locallyModifiedAndStaleCount : '-';

                if (this.controllerLocallyModifiedAndStaleCount > 0) {
                    $('#flow-status-container').find('.fa-exclamation-circle').removeClass('zero').addClass('locally-modified-and-stale');
                } else {
                    $('#flow-status-container').find('.fa-exclamation-circle').removeClass('locally-modified-and-stale').addClass('zero');
                }

                this.controllerSyncFailureCount =
                    nfCommon.isDefinedAndNotNull(status.syncFailureCount) ? status.syncFailureCount : '-';

                if (this.controllerSyncFailureCount > 0) {
                    $('#flow-status-container').find('.fa-question').removeClass('zero').addClass('sync-failure');
                } else {
                    $('#flow-status-container').find('.fa-question').removeClass('sync-failure').addClass('zero');
                }

            },

            /**
             * Updates the controller level bulletins
             *
             * @param response
             */
            updateBulletins: function (response) {
                this.bulletins.update(response);
            }
        }

        var flowStatusCtrl = new FlowStatusCtrl();
        return flowStatusCtrl;
    };
}));