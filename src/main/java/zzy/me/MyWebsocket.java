package zzy.me;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpRequest.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MyWebsocket {
    static VideoIndexer frame = null;
    static WebSocket webSocket = null; // 唯一的websocket连接

    static boolean validBookmark = false; // 导航到新页面时，是否是从书签导航而来
    static boolean markVideoStart = true; // 是否标记视频开始时间？

    static String scriptMarkVideo;  // 标记视频（制作书签）的JS语句段
    static String scriptPlayVideo;  // 播放书签的JS语句段

    // 当前websocket连接对象的要素（视频书签）：网址、<video>元素的网页索引、startTime、endTime属性、书签生成时间
    static String url = null;
    static int index = -1;
    static int startTime = -1;
    static int endTime = -1;
    static String detail = null;
    static String creationTime = null;

    static {
        try {
            Class<?> clazz = Class.forName("zzy.me.MyWebsocket");
            ClassLoader cLoader = clazz.getClassLoader();

            // 读取markVideo.js
            InputStream is = cLoader.getResourceAsStream("markVideo.js");
            BufferedInputStream bis = new BufferedInputStream(is);
            byte[] bytes = bis.readAllBytes();
            scriptMarkVideo = new String(bytes);
            scriptMarkVideo = scriptMarkVideo.replace("\r\n", " ");

            // 读取playVideo.js
            is = cLoader.getResourceAsStream("playVideo.js");
            bis = new BufferedInputStream(is);
            bytes = bis.readAllBytes();
            scriptPlayVideo = new String(bytes);
            scriptPlayVideo = scriptPlayVideo.replace("\r\n", " ");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 将时刻/时间格式化成钟表格式
    static String formatTime(int time) {
        int hour = time / 3600;
        int minute = (time - hour * 3600) / 60;
        int second = time % 60;
        return String.format("%02d", hour) + ":" + String.format("%02d", minute) + ":" + String.format("%02d", second);
    }

    // 以调试方式打开浏览器
    static boolean launchBrowserInDebuggingMode(String browserPath, String targetUrl) {
        java.util.List<String> cmd = new ArrayList<String>();
        cmd.add(browserPath);
        cmd.add("--start-maximized"); // 窗口启动最大化
        cmd.add("--remote-debugging-port=9222"); // 启动调试端口
        cmd.add(targetUrl);
        ProcessBuilder process = new ProcessBuilder(cmd);
        try {
            process.start();
        } catch (Exception ex) {
            return false;
        }

        return true;
    }

    // 根据指定url建议websocket连接
    static boolean buildWebSocketConnection(String targetUrl) {
        // 等待浏览器打开targetUrl指定的网页
        try {
            Thread.sleep(500);
        } catch (Exception ex) {
        }

        // 建立HTTP连接对象
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(5)).build();
        // 建立HTTP请求对象
        HttpRequest request = HttpRequest.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .method("GET", BodyPublishers.noBody()).timeout(Duration.ofSeconds(5))
                .uri(URI.create("http://localhost:9222/json/list")).build();
        // 发送请求 & 接收响应
        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            return false; // 无法访问http://localhost:9222/json/list（和运行本程序前已经启动了浏览器有关）
        }

        // 解析返回的JSON数据
        JSONArray jsonArray = new JSONArray(response.body());

        // 获取webSocketDebuggerUrl
        Iterator<Object> it = jsonArray.iterator();
        while (it.hasNext()) {
            JSONObject jsonObject = (JSONObject) it.next();
            String url = jsonObject.getString("url");

            if (url.equals(targetUrl)) {
                String webSocketUrl = jsonObject.getString("webSocketDebuggerUrl");

                // 建立唯一的websocket连接
                webSocket = HttpClient.newHttpClient().newWebSocketBuilder()
                        .buildAsync(URI.create(webSocketUrl), new WebSocket.Listener() {
                            StringJoiner joiner = new StringJoiner("");

                            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                webSocket.request(1);

                                joiner.add(data);
                                // 处理查询结果
                                if (last) {
                                    handleResponse(joiner.toString());
                                    joiner = new StringJoiner("");
                                }

                                return null;
                            }

                            public void onOpen(WebSocket webSocket) {
                                webSocket.request(1);

                                // 调试：打印websocket已经建立
                                System.out.println("");
                                System.out.println("websocket opened");
                                System.out.println("");
                            }
                        }).join();
                return true;
            }
        } // while

        return false;
    }

    public static String buildRequest(int id, String method, JSONObject jsonParams) {
        JSONObject jsonRequest = new JSONObject();
        jsonRequest.put("id", id);
        jsonRequest.put("method", method);
        if (jsonParams != null)
            jsonRequest.put("params", jsonParams);

        // 调试：打印websocket请求
        System.out.println("-> request: " + jsonRequest.toString());
        System.out.println("");

        return jsonRequest.toString();
    }

    // 取得视频信息
    public static void markVideo() {
        String script = scriptMarkVideo;
        JSONObject jsonParams = new JSONObject();
        jsonParams.put("expression", script);
        webSocket.sendText(buildRequest(4, "Runtime.evaluate", jsonParams), true);
    }

    // 根据书签播放指定视频
    public static void playVideo() {
        // 显示要播放的书签信息
        frame.lblVideoURL.setText(url);
        frame.lblStartTime.setText(formatTime(startTime));
        if (endTime >= 0) {
            frame.lblEndTime.setText(formatTime(endTime));
            frame.lblDuration.setText(formatTime(endTime - startTime));
        } else {
            frame.lblEndTime.setText("hh:mm:ss");
            frame.lblDuration.setText("hh:mm:ss");
        }
        frame.textAreaDetail.setText(detail);

        // 显示提示信息
        // 提示1："如果不能播放，直接手动播放"
        // 提示2："如果跳转位置不对，请再次双击此书签"
        frame.prompt("1.如果未播放,直接在视频上单击播放;2.如果跳转位置不对(网站有记忆功能),请再次双击书签");

        // 播放视频
        String script = scriptPlayVideo;
        script = script.replace("indexPlaceholder", String.valueOf(index));
        script = script.replace("startTimePlaceHolder", String.valueOf(startTime));
        JSONObject jsonParams = new JSONObject();
        jsonParams.put("expression", script);
        webSocket.sendText(buildRequest(5, "Runtime.evaluate", jsonParams), true);
    }

    // 处理websocket的响应（可能会发出进一步的请求）
    public static void handleResponse(String response) {
        // 调试：打印websocket响应
        System.out.println("<- response: " + response);
        System.out.println("");

        JSONObject jsonResponse = new JSONObject(response); // 将websocket响应转换成json对象
        int id = 0;
        try {
            id = jsonResponse.getInt("id"); // 从对请求的响应中获取请求id, 如果没有，则表明是订阅的事件
        } catch (JSONException ex) {
            String method = jsonResponse.getString("method");
            JSONObject jsonParams = jsonResponse.getJSONObject("params");

            if (method.equals("Inspector.detached")) {
                if (jsonParams.getString("reason").equals("target_closed")) { // websocket对应页面已经关闭
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok").join(); // 关闭websocket连接
                    webSocket = null;
                    frame.btnLaunchBrowser.setText("启动");

                    url = null;
                    index = -1;
                    startTime = -1;
                    endTime = -1;
                    detail = null;
                    creationTime = null;
                    frame.lblVideoURL.setText("------");
                    frame.lblStartTime.setText("hh:mm:ss");
                    frame.lblEndTime.setText("hh:mm:ss");
                    frame.lblDuration.setText("hh:mm:ss");
                    frame.textAreaDetail.setText("");

                    frame.listCellRenderer.setIndex(-1);                    
                    frame.listBookmarks.repaint();
                }
            } else if (method.equals("Page.frameNavigated")) { // websocket连接的页面导航到了新网址
                JSONObject jsonFrame = jsonParams.getJSONObject("frame");
                if (!jsonFrame.has("parentId")) {
                    if (validBookmark) { // 书签导航
                        if (jsonFrame.has("url")) {
                            // 获取新页面的url
                            url = jsonFrame.getString("url");
                            if (jsonFrame.has("urlFragment"))
                                url = url + jsonFrame.getString("urlFragment");
                        } else if (jsonFrame.has("unreachableUrl")) { // 这种情况几乎不可能出现
                            validBookmark = false;
                            url = jsonFrame.getString("unreachableUrl");
                            frame.prompt("该网址不可访问");
                        }

                        frame.lblVideoURL.setText(url);
                        frame.lblStartTime.setText(formatTime(startTime));
                        if (endTime >= 0) {
                            frame.lblEndTime.setText(formatTime(endTime));
                            frame.lblDuration.setText(formatTime(endTime - startTime));
                        } else {
                            frame.lblEndTime.setText("hh:mm:ss");
                            frame.lblDuration.setText("hh:mm:ss");
                        }
                        frame.textAreaDetail.setText(detail);
                    } else { // 地址栏导航
                        if (jsonFrame.has("unreachableUrl")) {
                            url = jsonFrame.getString("unreachableUrl");
                            frame.prompt("该网址不可访问");
                        } else if (jsonFrame.has("url")) {
                            // 获取新页面的url
                            url = jsonFrame.getString("url");
                            if (jsonFrame.has("urlFragment"))
                                url = url + jsonFrame.getString("urlFragment");
                        }

                        index = -1;
                        startTime = -1;
                        endTime = -1;
                        detail = null;
                        creationTime = null;

                        frame.lblVideoURL.setText(url);
                        frame.lblStartTime.setText("hh:mm:ss");
                        frame.lblEndTime.setText("hh:mm:ss");
                        frame.lblDuration.setText("hh:mm:ss");
                        frame.textAreaDetail.setText("");

                        frame.listCellRenderer.setIndex(-1);                    
                        frame.listBookmarks.repaint();
                    }
                }
            } else if (method.equals("Page.loadEventFired")) {
                if (validBookmark) { // 书签导航
                    validBookmark = false;
                    playVideo();
                }
            }

            return;
        }

        JSONObject jsonResult = null;
        try {
            jsonResult = jsonResponse.getJSONObject("result"); // 得到请求的响应结果
        } catch (JSONException ex) {
            return; // 可能返回了"error"而不是"result",不做任何事情
        }

        JSONObject jsonParams; // 要发出的命令的参数，json对象
        String script; // 要执行的js语句

        if (id == 1) { // DOM.enable
            webSocket.sendText(buildRequest(2, "Runtime.enable", null), true);
        } else if (id == 2) { // Runtime.enable
            webSocket.sendText(buildRequest(3, "Page.enable", null), true);
        } else if (id == 3) { // Page.enable
        } else if (id == 4) { // Runtime.evaluate: 搜索唯一可标记的视频，返回该视频的url页面链接/索引/startTime/endTime属性
            jsonResult = jsonResult.getJSONObject("result");
            if (jsonResult.getString("type").equals("string")) {
                String value = jsonResult.getString("value");
                String[] videoBookmarkAttributes = value.split(",");
                if (videoBookmarkAttributes.length == 3) {
                    index = Integer.parseInt(videoBookmarkAttributes[1]);

                    if (markVideoStart) {
                        startTime = (int) Float.parseFloat(videoBookmarkAttributes[2]);
                        frame.lblStartTime.setText(formatTime(startTime));
                    } else {
                        endTime = (int) Float.parseFloat(videoBookmarkAttributes[2]);
                        frame.lblEndTime.setText(formatTime(endTime));
                    }

                    // 显示持续时间
                    if (startTime >= 0 && endTime >= 0) {
                        int duration = endTime - startTime;
                        if (duration >= 0)
                            frame.lblDuration.setText(formatTime(duration));
                        else
                            frame.lblDuration.setText("-" + formatTime(-duration));
                    }
                } else {
                    frame.prompt("当前页面上没有视频，或者无法确定需要标记的视频");
                }
            } else {
                frame.prompt("当前页面上没有视频，或者无法确定需要标记的视频，或者无法跨源访问视频");
            }
        } else if (id == 5) { // Runtime.evaluate: 按照书签跳转并在指定位置处播放视频（有自动播放问题），操作videoB对象
            jsonResult = jsonResult.getJSONObject("result");
            if (!(jsonResult.getString("type").equals("object")
                    && jsonResult.getString("className").equals("Promise"))) {
                playVideo();
            }
        } else if (id == 6) { // Page.bringToFront: 切换tab
            if (validBookmark) {
                validBookmark = false;
                script = "currentUrl = window.location.href;";
                jsonParams = new JSONObject();
                jsonParams.put("expression", script);
                webSocket.sendText(buildRequest(7, "Runtime.evaluate", jsonParams), true);
            }
        } else if (id == 7) { // Runtime.evaluate: 获取当前网页链接
            jsonResult = jsonResult.getJSONObject("result");
            String currentLink = jsonResult.getString("value");
            if (currentLink.equals(url)) { // 直接播放
                playVideo();
            } else {
                validBookmark = true; // 书签导航
                jsonParams = new JSONObject();
                jsonParams.put("url", url);
                webSocket.sendText(buildRequest(8, "Page.navigate", jsonParams), true); // 重新加载页面
            }
        } else if (id == 8) { // Page.navigate: 重新加载页面后播放视频
            // 重新加载页面不成功，在状态栏上提示原因
            if (jsonResult.has("errorText")) {
                validBookmark = false;
                String errorText = jsonResult.getString("errorText");
                frame.prompt(errorText);
            }
        }
    }
}
