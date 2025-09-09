(function () {
    document.querySelectorAll('tr[data-site-uri]').forEach(row => {
        const url = row.getAttribute('data-site-uri');
        console.log(url);
        row.querySelector('.remove-site').addEventListener('click', event => {
            event.preventDefault();
            try {
                JJI.deleteSite(url, function () {
                    row.remove();
                    if (document.querySelector('#sites tbody').childElementCount === 0) {
                        window.location.reload();
                    }
                });
            } catch (e) {
                alert('Unable to delete site.');
            }
        });
        row.querySelector('.navigate-to-site').addEventListener('click', event => {
            event.preventDefault();
            try {
                JJI.getSiteUrl(url, function (response) {
                    if (!response.responseJSON.startsWith('http://') && !response.responseJSON.startsWith('https://')) {
                        alert(response.responseJSON);
                    } else if (confirm('Navigate to: ' + response.responseJSON)) {
                        window.location = response.responseJSON;
                    }
                });
            } catch (e) {
                alert('Unable to navigate to site.');
            }
        });
    });
})();
