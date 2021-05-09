if (typeof videoB == 'undefined') {
    searchVideoByIndex = function (doc, index) {
        var videos = doc.getElementsByTagName('video');
        for (let i = 0; i < videos.length; i++) {
            if (videos[i].src != null) {
                if (videoIndex == index) {
                    return videos[i];
                }
                videoIndex++;
            }
        }
        var iframes = doc.getElementsByTagName('iframe');
        for (let j = 0; j < iframes.length; j++) {
            var subDoc = iframes[j].contentWindow.document;
            var video = searchVideoByIndex(subDoc, index);
            if (video != null)
                return video;
        }
        return null;
    };
}
videoIndex = 0;
videoB = searchVideoByIndex(document, index);
if (videoB != null) {
    videoB.currentTime = startTime;
    videoB.scrollIntoViewIfNeeded();
    videoB.play();
}
