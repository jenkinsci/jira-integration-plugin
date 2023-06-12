(function () {
    if (window.jQuery === window.$$) {
        // jQuery is used as framework.
        $$(document).ajaxError(function (event, jqXhr) {
            alert(jqXhr.statusText);
        });
    } else {
        // Assume Prototype is the framework used, like the JavaScriptMethod binding does.
        Ajax.Responders.register({
            onComplete: function (response) {
                if (!response.success()) {
                    alert(response.transport.statusText);
                }
            }
        });
    }

    $$('tr[data-site-uri] a').each(function (element) {
        element.onclick = function () {
            let row = this.up('tr');
            let url = row.readAttribute('data-site-uri');

            if (this.hasClassName('remove-site')) {
                JJI.deleteSite(url, function () {
                    if (row.up('tbody').childElements('tr').length === 1) {
                        window.location.reload();
                    } else {
                        row.remove();
                    }
                });
            } else if (this.hasClassName('navigate-to-site')) {
                JJI.getSiteUrl(url, function (response) {
                    if (confirm('Navigate to: ' + response.responseJSON)) {
                        window.location = response.responseJSON;
                    }
                });
            } else if (this.hasClassName('refresh-tunnel')) {
                JJI.refreshTunnel(url, function (response) {
                    alert('Refreshing tunnel');
                });
            }
        };
    });
})();
