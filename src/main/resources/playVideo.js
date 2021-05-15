if (typeof videoB == 'undefined') {
    isValidVideoElement = function (video) {
        if (video.src != null)
            return true;
        var c = video.children;
        for (let k = 0; k < c.length; k++) {
            if (c[k].tagName == 'source' && c[k].src != null) {
                return true;
            }
        }
        return false;
    };
    searchVideoByIndex = function (doc, index) {
        var videos = doc.getElementsByTagName('video');
        for (let i = 0; i < videos.length; i++) {
            if (isValidVideoElement(videos[i])) {
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
videoB = searchVideoByIndex(document, indexPlaceholder);
if (videoB != null) {
    videoB.currentTime = startTimePlaceHolder;
    videoB.scrollIntoViewIfNeeded();
    videoB.play();
}