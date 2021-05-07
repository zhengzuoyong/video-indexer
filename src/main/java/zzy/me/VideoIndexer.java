package zzy.me;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import com.formdev.flatlaf.FlatLightLaf;
import org.json.*;
import java.util.*;
import java.io.*;
import java.nio.*;
import java.text.*;

public class VideoIndexer extends JFrame {
	JSONObject jsonRoot; // 书签对象

	java.util.Timer timer = new java.util.Timer(true); // 计时器对象

	JPanel contentPane;

	JComboBox<String> comboBrowser;
	JTextField textFieldBrowserPath;
	JButton btnLaunchBrowser; // "启动浏览器" / "切换"按钮

	// 视频书签信息
	JLabel lblVideoURL, lblStartTime, lblEndTime, lblDuration;
	JTextArea textAreaDetail;

	// 书签列表框
	JList<JSONObject> listBookmarks;
	DefaultListModel<JSONObject> listModel;
	MyListCellRenderer listCellRenderer;

	JLabel lblPrompt; // 操作提示

	public static void main(String[] args) {
		FlatLightLaf.install();

		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					// 创建框架窗口
					VideoIndexer frame = new VideoIndexer("网络视频书签");
					MyWebsocket.frame = frame;
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public VideoIndexer(String frameTitle) {
		super(frameTitle);

		// 读取videoBookmark.json文件，生成jsonRoot对象
		readBookmark();

		// 得到屏幕分辨率
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int screenWidth = (int) screenSize.getWidth();
		int screenHeight = (int) screenSize.getHeight();

		// 创建并设置框架窗口
		int winWidth = 549;
		int winHeight = 700;
		setSize(winWidth, winHeight);
		setLocation(screenWidth - winWidth - 50, (screenHeight - winHeight) / 2);
		setBackground(new Color(224, 255, 255));
		setResizable(false);
		// setAlwaysOnTop(true);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		contentPane = new JPanel();
		setContentPane(contentPane);
		contentPane.setLayout(null);

		JLabel lblBrowser = new JLabel("浏览器");
		lblBrowser.setFont(new Font("宋体", Font.PLAIN, 12));
		lblBrowser.setBounds(10, 14, 83, 15);
		contentPane.add(lblBrowser);

		// 浏览器选择：Chrome/Edge
		comboBrowser = new JComboBox<String>();
		comboBrowser.setMaximumRowCount(2);
		comboBrowser.setFont(new Font("Consolas", Font.PLAIN, 12));
		comboBrowser.setBounds(64, 10, 75, 21);
		comboBrowser.setModel(new DefaultComboBoxModel<String>(new String[] { "Chrome", "Edge" }));
		contentPane.add(comboBrowser);
		String selectedBrowser = jsonRoot.getJSONObject("browser").getString("selectedBrowser");
		if (selectedBrowser.equals("Chrome"))
			comboBrowser.setSelectedIndex(0);
		else
			comboBrowser.setSelectedIndex(1);
		comboBrowser.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String selectedBrowser = (String) comboBrowser.getSelectedItem();
				setBrowserPath(selectedBrowser);
			}
		});

		// 浏览器路径
		textFieldBrowserPath = new JTextField();
		textFieldBrowserPath.setFont(new Font("Consolas", Font.PLAIN, 12));
		textFieldBrowserPath.setBounds(149, 10, 258, 21);
		contentPane.add(textFieldBrowserPath);
		setBrowserPath(selectedBrowser);

		// "定位浏览器"按钮
		JButton btnSearchBrowser = new JButton("...");
		btnSearchBrowser.setFont(new Font("宋体", Font.PLAIN, 12));
		btnSearchBrowser.setBounds(417, 11, 31, 21);
		contentPane.add(btnSearchBrowser);
		btnSearchBrowser.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser fc = new JFileChooser();
				fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
				if (comboBrowser.getSelectedIndex() == 0)
					fc.setCurrentDirectory(new File("C:\\Program Files (x86)\\Google\\Chrome\\Application")); // Chrome的默认安装路径
				else if (comboBrowser.getSelectedIndex() == 1)
					fc.setCurrentDirectory(new File("C:\\Program Files (x86)\\Microsoft\\Edge\\Application")); // Edge的默认安装路径
				fc.showDialog(new JLabel(), "选择浏览器可执行程序");
				File file = fc.getSelectedFile();
				if (file != null)
					textFieldBrowserPath.setText(file.getAbsolutePath());
			}
		});

		// "启动浏览器" / "切换"按钮
		btnLaunchBrowser = new JButton("启动");
		btnLaunchBrowser.setFont(new Font("宋体", Font.PLAIN, 12));
		btnLaunchBrowser.setBounds(456, 11, 67, 21);
		contentPane.add(btnLaunchBrowser);
		btnLaunchBrowser.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (MyWebsocket.webSocket == null) { // 打开新的空白页面
					String browserType = comboBrowser.getSelectedItem().toString(); // "Chrome" / "Edge"
					String targetUrl = (browserType.equals("Chrome")) ? "chrome://newtab/" : "edge://newtab/"; // Chrome/Edge的默认空白网址
					lblVideoURL.setText(targetUrl);
					launchBrowserInDebuggingMode(targetUrl);
				} else { // 切换到已有页面
					MyWebsocket.webSocket.sendText(MyWebsocket.buildRequest(6, "Page.bringToFront", null), true);
				}
			}
		});

		JLabel lblUrl = new JLabel("URL");
		lblUrl.setFont(new Font("Consolas", Font.PLAIN, 12));
		lblUrl.setBounds(10, 50, 21, 15);
		contentPane.add(lblUrl);

		// 网页URL
		lblVideoURL = new JLabel("------");
		lblVideoURL.setForeground(new Color(128, 0, 0));
		lblVideoURL.setFont(new Font("Consolas", Font.PLAIN, 12));
		lblVideoURL.setBounds(68, 50, 455, 15);
		contentPane.add(lblVideoURL);

		// 起始时间
		lblStartTime = new JLabel("hh:mm:ss");
		lblStartTime.setForeground(new Color(128, 0, 0));
		lblStartTime.setFont(new Font("Consolas", Font.PLAIN, 12));
		lblStartTime.setBounds(94, 73, 68, 15);
		contentPane.add(lblStartTime);

		// 结束时间
		lblEndTime = new JLabel("hh:mm:ss");
		lblEndTime.setForeground(new Color(128, 0, 0));
		lblEndTime.setFont(new Font("Consolas", Font.PLAIN, 12));
		lblEndTime.setBounds(270, 73, 68, 15);
		contentPane.add(lblEndTime);

		// 持续时间
		lblDuration = new JLabel("hh:mm:ss");
		lblDuration.setForeground(new Color(0, 128, 0));
		lblDuration.setFont(new Font("Consolas", Font.PLAIN, 12));
		lblDuration.setBounds(180, 73, 70, 15);
		contentPane.add(lblDuration);

		// "标记起始时间"按钮
		JButton btnMarkStartTime = new JButton("标记起始时间");
		btnMarkStartTime.setFont(new Font("宋体", Font.PLAIN, 12));
		btnMarkStartTime.setBounds(68, 88, 103, 21);
		contentPane.add(btnMarkStartTime);
		btnMarkStartTime.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (MyWebsocket.webSocket != null) {
					MyWebsocket.markVideoStart = true;
					MyWebsocket.markVideo();

				} else {
					prompt("没有建立websocket连接，无法标记起始时间");
				}
			}
		});

		// "标记结束时间"按钮
		JButton btnMarkEndTime = new JButton("标记结束时间");
		btnMarkEndTime.setFont(new Font("宋体", Font.PLAIN, 12));
		btnMarkEndTime.setBounds(246, 88, 103, 21);
		contentPane.add(btnMarkEndTime);
		btnMarkEndTime.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (MyWebsocket.webSocket != null) {
					MyWebsocket.markVideoStart = false;
					MyWebsocket.markVideo();
				} else {
					prompt("没有建立websocket连接，无法标记结束时间");
				}
			}
		});

		// "记录书签"按钮
		JButton btnRecordNewBookmark = new JButton("记录");
		btnRecordNewBookmark.setFont(new Font("宋体", Font.PLAIN, 12));
		btnRecordNewBookmark.setBounds(389, 88, 67, 23);
		contentPane.add(btnRecordNewBookmark);
		btnRecordNewBookmark.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (MyWebsocket.url == null || MyWebsocket.index == -1 || MyWebsocket.startTime == -1) {
					prompt("无效的视频书签");
					return;
				}

				if (MyWebsocket.endTime >= 0) {
					if (MyWebsocket.startTime >= MyWebsocket.endTime) {
						prompt("无效的视频书签：起始时间 >= 结束时间");
						return;
					}
				}

				// 生成新书签
				JSONObject jsonBookmark = new JSONObject();
				jsonBookmark.put("url", MyWebsocket.url);
				jsonBookmark.put("index", MyWebsocket.index);
				jsonBookmark.put("startTime", MyWebsocket.startTime);
				if (MyWebsocket.endTime >= 0)
					jsonBookmark.put("endTime", MyWebsocket.endTime);
				jsonBookmark.put("detail", textAreaDetail.getText());
				jsonBookmark.put("creationTime", new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(new Date()));

				// 保存
				jsonRoot.getJSONArray("bookmarks").put(jsonBookmark);
				writeBookmark();

				// 列表框里显示新书签
				listModel.insertElementAt(jsonBookmark, 0); // 保持倒序排列
				listCellRenderer.setIndex(0);                    
				listBookmarks.repaint();
			}
		});

		// "删除书签"按钮
		JButton btnDeleteBookmark = new JButton("删除");
		btnDeleteBookmark.setFont(new Font("宋体", Font.PLAIN, 12));
		btnDeleteBookmark.setBounds(456, 88, 67, 23);
		contentPane.add(btnDeleteBookmark);
		btnDeleteBookmark.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// 从列表框中删除
				java.util.List<JSONObject> deleteList = listBookmarks.getSelectedValuesList();
				deleteList.forEach((obj) -> {
					listModel.removeElement(obj);
				});

				JSONArray jsonBookmarks = jsonRoot.getJSONArray("bookmarks");
				deleteList.forEach((obj) -> {
					for (int i = 0; i < jsonBookmarks.length(); i++) {
						JSONObject jsonBookmark = (JSONObject) jsonBookmarks.get(i);
						if (jsonBookmark.getString("creationTime").equals(obj.getString("creationTime"))) {
							jsonBookmarks.remove(i);
							break;
						}
					}
				});

				writeBookmark();
			}
		});

		JLabel lblDetail = new JLabel("书签详情");
		lblDetail.setFont(new Font("宋体", Font.PLAIN, 12));
		lblDetail.setBounds(10, 127, 48, 15);
		contentPane.add(lblDetail);

		// 书签详情
		textAreaDetail = new JTextArea();
		textAreaDetail.setLocale(Locale.SIMPLIFIED_CHINESE);
		textAreaDetail.setLineWrap(true);
		textAreaDetail.setFont(new Font("宋体", Font.PLAIN, 12));
		textAreaDetail.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		textAreaDetail.setBounds(68, 120, 455, 46);
		contentPane.add(textAreaDetail);

		// 滚动面板
		JScrollPane scrollPaneBookmarks = new JScrollPane();
		scrollPaneBookmarks.setBounds(10, 180, 514, 454);
		contentPane.add(scrollPaneBookmarks);

		// 列表框（列出所有记录的书签信息, 排序方式：生成时间倒序，最后生成的书签排在最前边显示）
		listBookmarks = new JList<JSONObject>();
		scrollPaneBookmarks.setViewportView(listBookmarks);
		java.util.List<JSONObject> bookmarks = new java.util.ArrayList<JSONObject>();
		JSONArray jsonBookmarks = jsonRoot.getJSONArray("bookmarks");
		for (int i = 0; i < jsonBookmarks.length(); i++) {
			JSONObject jsonBookmark = (JSONObject) jsonBookmarks.get(i);
			bookmarks.add(jsonBookmark);
		}
		bookmarks.sort((o1, o2) -> {
			JSONObject jo1 = (JSONObject) o1;
			JSONObject jo2 = (JSONObject) o2;
			return jo2.getString("creationTime").compareTo(jo1.getString("creationTime"));
		});
		listModel = new DefaultListModel<JSONObject>();
		bookmarks.forEach((o) -> {
			listModel.addElement((JSONObject) o);
		});
		listBookmarks.setModel(listModel); // 列表框的数据都是JSON对象（书签）
		listBookmarks.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		listCellRenderer = new MyListCellRenderer();
		listBookmarks.setCellRenderer(listCellRenderer); // 列表框自绘
		listBookmarks.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) { // 鼠标双击
					// 立刻重绘
					listCellRenderer.setIndex(listBookmarks.getSelectedIndex());
					listBookmarks.repaint();

					// 获取鼠标双击选择的书签信息
					JSONObject selectedBookmark = listBookmarks.getSelectedValue();
					MyWebsocket.url = selectedBookmark.getString("url");
					MyWebsocket.index = selectedBookmark.getInt("index");
					MyWebsocket.startTime = selectedBookmark.getInt("startTime");
					if (selectedBookmark.has("endTime"))
						MyWebsocket.endTime = selectedBookmark.getInt("endTime");
					else
						MyWebsocket.endTime = -1;
					MyWebsocket.detail = selectedBookmark.getString("detail");
					MyWebsocket.creationTime = selectedBookmark.getString("creationTime");

					MyWebsocket.validBookmark = true; // 书签导航
					if (MyWebsocket.webSocket == null)
						launchBrowserInDebuggingMode(MyWebsocket.url);
					else
						MyWebsocket.webSocket.sendText(MyWebsocket.buildRequest(6, "Page.bringToFront", null), true);
				}
			}
		});

		// 操作提示
		lblPrompt = new JLabel("");
		lblPrompt.setForeground(new Color(255, 0, 0));
		lblPrompt.setFont(new Font("宋体", Font.PLAIN, 12));
		lblPrompt.setBounds(10, 642, 513, 15);
		contentPane.add(lblPrompt);
	}

	// 设置浏览器路径
	public void setBrowserPath(String selectedBrowser) {
		// 设置浏览器路径
		JSONArray jsonAvailableBrowsers = jsonRoot.getJSONObject("browser").getJSONArray("availableBrowsers");
		int i;
		for (i = 0; i < jsonAvailableBrowsers.length(); i++) {
			JSONObject jsonAvailableBrowser = (JSONObject) jsonAvailableBrowsers.get(i);
			if (jsonAvailableBrowser.getString("type").equals(selectedBrowser)) {
				textFieldBrowserPath.setText(jsonAvailableBrowser.getString("path"));
				break;
			}
		}
		if (i == jsonAvailableBrowsers.length())
			textFieldBrowserPath.setText("");
	}

	// 读取videoBookmark.json配置文件, 生成jsonRoot对象
	public void readBookmark() {
		// 读取书签配置信息
		File file = new File("videoBookmark.json");
		if (file.exists()) {
			CharBuffer cb = CharBuffer.allocate((int) file.length());
			BufferedReader br;
			try {
				br = new BufferedReader(new FileReader(file));
				br.read(cb);
				cb.flip();
				br.close();
			} catch (Exception e) {
				e.printStackTrace();
			}

			// 生成json配置对象
			jsonRoot = new JSONObject(cb.toString());
		} else {
			JSONObject jsonAvailableBrowser = new JSONObject();
			jsonAvailableBrowser.put("type", "Chrome");
			jsonAvailableBrowser.put("path", "");

			JSONArray jsonavailableBrowsers = new JSONArray();
			jsonavailableBrowsers.put(jsonAvailableBrowser);

			JSONObject jsonBrowser = new JSONObject();
			jsonBrowser.put("selectedBrowser", "Chrome");
			jsonBrowser.put("availableBrowsers", jsonavailableBrowsers);

			JSONArray jsonBookmarks = new JSONArray();

			// 生成初始json配置对象
			jsonRoot = new JSONObject();
			jsonRoot.put("browser", jsonBrowser);
			jsonRoot.put("bookmarks", jsonBookmarks);
		}
	}

	// 将jsonRoot对象内容保存至videoBookmark.json配置文件
	public void writeBookmark() {
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter("videoBookmark.json"));
			bw.write(jsonRoot.toString());
			bw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// 打开浏览器访问指定网址
	public void launchBrowserInDebuggingMode(String targetUrl) {
		String browserPath = textFieldBrowserPath.getText();

		if (MyWebsocket.launchBrowserInDebuggingMode(browserPath, targetUrl)) {
			// 保存浏览器类型和路径
			String browserType = comboBrowser.getSelectedItem().toString(); // "Chrome" / "Edge"
			JSONObject jsonBrowser = jsonRoot.getJSONObject("browser");
			jsonBrowser.put("selectedBrowser", browserType);
			JSONArray jsonAvailableBrowsers = jsonBrowser.getJSONArray("availableBrowsers");
			int i;
			for (i = 0; i < jsonAvailableBrowsers.length(); i++) {
				JSONObject jsonAvailableBrowser = (JSONObject) jsonAvailableBrowsers.get(i);
				if (jsonAvailableBrowser.getString("type").equals(browserType)) {
					jsonAvailableBrowser.put("path", browserPath);
					break;
				}
			}
			if (i == jsonAvailableBrowsers.length()) {
				JSONObject jsonAvailableBrowser = new JSONObject();
				jsonAvailableBrowser.put("type", browserType);
				jsonAvailableBrowser.put("path", browserPath);
				jsonAvailableBrowsers.put(jsonAvailableBrowser);
			}
			writeBookmark();

			if (MyWebsocket.buildWebSocketConnection(targetUrl)) {
				btnLaunchBrowser.setText("切换");
				// 建立websocket连接后立即发出第一条命令DOM.enable
				MyWebsocket.webSocket.sendText(MyWebsocket.buildRequest(1, "DOM.enable", null), true);
			} else {
				prompt("无法建立websocket连接，请关闭所有正在运行的浏览器后再试"); // 运行本程序前已经有浏览器在运行，无法建立websocket连接
			}
		} else {
			prompt("无法启动浏览器，请检查浏览器程序的路径是否设置正确"); // 无法正常启动浏览器
		}
	}

	// 在状态栏上提示3秒钟
	public void prompt(String prompt) {
		lblPrompt.setText(prompt);

		// 取消当前定时任务
		timer.cancel();

		// 3秒钟后清除
		timer = new java.util.Timer(true); // 重新生成计时器对象
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.SECOND, 3);
		timer.schedule(new TimerTask() {
			public void run() {
				lblPrompt.setText("");
			}
		}, calendar.getTime());
	}
}
