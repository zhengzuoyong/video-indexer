if (typeof videoBookmark == 'undefined') {
    searchAllVideos = function (doc) {
        var videos = doc.getElementsByTagName('video');
        for (let i = 0; i < videos.length; i++) {
            if (videos[i].src != null) {
                if (videos[i].paused)
                    pausedVideos.push({ videoElement: videos[i], index: videoIndex });
                else
                    liveVideos.push({ videoElement: videos[i], index: videoIndex });
                videoIndex++;
            }
        }
        var iframes = doc.getElementsByTagName('iframe');
        for (let j = 0; j < iframes.length; j++) {
            var subDoc = iframes[j].contentWindow.document;
            searchAllVideos(subDoc);
        }
    };
}
liveVideos = [];
pausedVideos = [];
videoIndex = 0;
searchAllVideos(document);
if (liveVideos.length == 1) {
    videoA = liveVideos[0];
    videoBookmark = window.location.href + ',' + videoA.index + ',' + videoA.videoElement.currentTime;
}
else if (liveVideos.length == 0 && pausedVideos.length == 1) {
    videoA = pausedVideos[0];
    videoBookmark = window.location.href + ',' + videoA.index + ',' + videoA.videoElement.currentTime;
}
else {
    videoBookmark = null;
}
