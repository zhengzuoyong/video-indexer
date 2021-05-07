package zzy.me;

import javax.swing.*;
import java.awt.*;

import org.json.*;

public class MyListCellRenderer extends JPanel implements ListCellRenderer<Object> {
    private static final Color LIGHT_GRAY = new Color(224, 224, 224);
    private static final Color LIGHT_RED = new Color(255, 96, 96);

    private int index = -1;

    private JLabel lblURL = new JLabel();
    private JLabel lblDetail = new JLabel();

    public MyListCellRenderer() {
        setOpaque(true);
        this.setLayout(new GridLayout(2, 1));
        this.add(lblURL);
        this.add(lblDetail);
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
            boolean cellHasFocus) {
        // 标签数据
        JSONObject jsonBookmark = (JSONObject) value;
        String url = jsonBookmark.getString("url");
        String detail = MyWebsocket.formatTime(jsonBookmark.getInt("startTime"));
        if (jsonBookmark.has("endTime"))
            detail = detail + "-" + MyWebsocket.formatTime(jsonBookmark.getInt("endTime"));
        detail = detail + " " + jsonBookmark.getString("detail");
        // 显示标签信息
        lblURL.setText(url);
        lblDetail.setText(detail);

        // 设置背景色
        Color background = null;
        if (isSelected) {
            background = Color.GREEN;
        } else {
            if (this.index == index) {
                background = LIGHT_RED;
            } else {
                if (index % 2 == 0)
                    background = Color.WHITE;
                else
                    background = LIGHT_GRAY;
            }
        }
        setBackground(background);

        return this;
    }
}
