package com.fangxuele.tool.push.ui.listener;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.swing.ClipboardUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fangxuele.tool.push.dao.TPushHistoryMapper;
import com.fangxuele.tool.push.domain.TPushHistory;
import com.fangxuele.tool.push.logic.PushData;
import com.fangxuele.tool.push.ui.form.MainWindow;
import com.fangxuele.tool.push.ui.form.MemberForm;
import com.fangxuele.tool.push.ui.form.PushHisForm;
import com.fangxuele.tool.push.util.MybatisUtil;
import com.opencsv.CSVReader;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <pre>
 * 推送历史管理tab相关事件监听
 * </pre>
 *
 * @author <a href="https://github.com/rememberber">RememBerBer</a>
 * @since 2017/6/16.
 */
public class PushHisListener {
    private static final Log logger = LogFactory.get();

    private static boolean selectAllToggle = false;

    private static TPushHistoryMapper pushHistoryMapper = MybatisUtil.getSqlSession().getMapper(TPushHistoryMapper.class);

    public static void addListeners() {
        // 点击左侧表格事件
        PushHisForm.pushHisForm.getPushHisLeftTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ThreadUtil.execute(() -> {
                    PushHisForm.pushHisForm.getPushHisTextArea().setText("");

                    int selectedRow = PushHisForm.pushHisForm.getPushHisLeftTable().getSelectedRow();
                    String selectedId = PushHisForm.pushHisForm.getPushHisLeftTable()
                            .getValueAt(selectedRow, 4).toString();
                    TPushHistory tPushHistory = pushHistoryMapper.selectByPrimaryKey(Integer.valueOf(selectedId));
                    File pushHisFile = new File(tPushHistory.getCsvFile());

                    try {
                        BufferedReader br = new BufferedReader(new FileReader(pushHisFile));
                        String line = br.readLine();
                        long count = 0;
                        while (StringUtils.isNotEmpty(line)) {
                            PushHisForm.pushHisForm.getPushHisTextArea().append(line);
                            PushHisForm.pushHisForm.getPushHisTextArea().append("\n");
                            line = br.readLine();
                            count++;
                        }

                        PushHisForm.pushHisForm.getPushHisCountLabel().setText("共" + count + "条");
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }

                });
                super.mouseClicked(e);
            }
        });

        // 推送历史管理-全选
        PushHisForm.pushHisForm.getPushHisLeftSelectAllButton().addActionListener(e -> ThreadUtil.execute(() -> {
            toggleSelectAll();
            DefaultTableModel tableModel = (DefaultTableModel) PushHisForm.pushHisForm.getPushHisLeftTable()
                    .getModel();
            int rowCount = tableModel.getRowCount();
            for (int i = 0; i < rowCount; i++) {
                tableModel.setValueAt(selectAllToggle, i, 0);
            }
        }));

        // 推送历史管理-删除
        PushHisForm.pushHisForm.getPushHisLeftDeleteButton().addActionListener(e -> ThreadUtil.execute(() -> {
            try {
                DefaultTableModel tableModel = (DefaultTableModel) PushHisForm.pushHisForm.getPushHisLeftTable()
                        .getModel();
                int rowCount = tableModel.getRowCount();

                int selectedCount = 0;
                for (int i = 0; i < rowCount; i++) {
                    boolean isSelected = (boolean) tableModel.getValueAt(i, 0);
                    if (isSelected) {
                        selectedCount++;
                    }
                }

                if (selectedCount == 0) {
                    JOptionPane.showMessageDialog(MainWindow.mainWindow.getSettingPanel(), "请至少选择一个！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    int isDelete = JOptionPane.showConfirmDialog(MainWindow.mainWindow.getSettingPanel(), "确认删除？", "确认",
                            JOptionPane.YES_NO_OPTION);
                    if (isDelete == JOptionPane.YES_OPTION) {
                        for (int i = 0; i < rowCount; ) {
                            boolean delete = (boolean) tableModel.getValueAt(i, 0);
                            if (delete) {
                                Integer selectedId = (Integer) tableModel.getValueAt(i, 4);

                                TPushHistory tPushHistory = pushHistoryMapper.selectByPrimaryKey(selectedId);

                                File msgTemplateDataFile = new File(tPushHistory.getCsvFile());
                                if (msgTemplateDataFile.exists()) {
                                   msgTemplateDataFile.delete();
                                }
                                pushHistoryMapper.deleteByPrimaryKey(selectedId);
                                tableModel.removeRow(i);

                                i = 0;
                                rowCount = tableModel.getRowCount();
                            } else {
                                i++;
                            }
                        }
                        PushHisForm.pushHisForm.getPushHisLeftTable().updateUI();
                        MemberForm.init();
                    }
                }
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(MainWindow.mainWindow.getSettingPanel(), "删除失败！\n\n" + e1.getMessage(), "失败",
                        JOptionPane.ERROR_MESSAGE);
                logger.error(e1);
            }
        }));

        // 推送历史管理-复制按钮
        PushHisForm.pushHisForm.getPushHisCopyButton().addActionListener(e -> ThreadUtil.execute(() -> {
            try {
                PushHisForm.pushHisForm.getPushHisCopyButton().setEnabled(false);
                JOptionPane.showMessageDialog(MainWindow.mainWindow.getSettingPanel(), "内容已经复制到剪贴板！", "复制成功",
                        JOptionPane.INFORMATION_MESSAGE);
                ClipboardUtil.setStr(PushHisForm.pushHisForm.getPushHisTextArea().getText());
            } catch (Exception e1) {
                logger.error(e1);
            } finally {
                PushHisForm.pushHisForm.getPushHisCopyButton().setEnabled(true);
            }

        }));

        // 推送历史管理-导出按钮
        PushHisForm.pushHisForm.getPushHisExportButton().addActionListener(e -> {
            List<String> toExportFilePathList = new ArrayList<>();
            int selectedCount = 0;

            try {
                DefaultTableModel tableModel = (DefaultTableModel) PushHisForm.pushHisForm.getPushHisLeftTable()
                        .getModel();
                int rowCount = tableModel.getRowCount();
                for (int i = 0; i < rowCount; i++) {
                    boolean selected = (boolean) tableModel.getValueAt(i, 0);
                    if (selected) {
                        selectedCount++;
                        Integer selectedId = (Integer) tableModel.getValueAt(i, 4);
                        TPushHistory tPushHistory = pushHistoryMapper.selectByPrimaryKey(selectedId);
                        File msgTemplateDataFile = new File(tPushHistory.getCsvFile());
                        if (msgTemplateDataFile.exists()) {
                            toExportFilePathList.add(msgTemplateDataFile.getAbsolutePath());
                        }
                    }
                }

                if (selectedCount > 0) {
                    JFileChooser fileChooser = new JFileChooser();
                    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    int approve = fileChooser.showOpenDialog(MainWindow.mainWindow.getSettingPanel());
                    String exportPath = "";
                    if (approve == JFileChooser.APPROVE_OPTION) {
                        exportPath = fileChooser.getSelectedFile().getAbsolutePath();
                    } else {
                        return;
                    }

                    for (String toExportFilePath : toExportFilePathList) {
                        FileUtil.copy(toExportFilePath, exportPath, true);
                    }
                    JOptionPane.showMessageDialog(MainWindow.mainWindow.getSettingPanel(), "导出成功！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                    try {
                        Desktop desktop = Desktop.getDesktop();
                        desktop.open(new File(exportPath));
                    } catch (Exception e2) {
                        logger.error(e2);
                    }
                } else {
                    JOptionPane.showMessageDialog(MainWindow.mainWindow.getSettingPanel(), "请至少选择一个！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                }

            } catch (Exception e1) {
                JOptionPane.showMessageDialog(MainWindow.mainWindow.getSettingPanel(), "导出失败！\n\n" + e1.getMessage(), "失败",
                        JOptionPane.ERROR_MESSAGE);
                logger.error(e1);
            }

        });

        // 重发
        PushHisForm.pushHisForm.getResendFromHisButton().addActionListener(e -> ThreadUtil.execute(() -> {

            List<String> toImportFilePathList = new ArrayList<>();
            int selectedCount = 0;
            CSVReader reader = null;
            try {
                DefaultTableModel tableModel = (DefaultTableModel) PushHisForm.pushHisForm.getPushHisLeftTable()
                        .getModel();
                int rowCount = tableModel.getRowCount();
                for (int i = 0; i < rowCount; i++) {
                    boolean selected = (boolean) tableModel.getValueAt(i, 0);
                    if (selected) {
                        selectedCount++;
                        Integer selectedId = (Integer) tableModel.getValueAt(i, 4);
                        TPushHistory tPushHistory = pushHistoryMapper.selectByPrimaryKey(selectedId);
                        File msgTemplateDataFile = new File(tPushHistory.getCsvFile());
                        if (msgTemplateDataFile.exists()) {
                            toImportFilePathList.add(msgTemplateDataFile.getAbsolutePath());
                        }
                    }
                }

                if (selectedCount > 0) {
                    MainWindow.mainWindow.getTabbedPane().setSelectedIndex(3);
                    PushData.allUser = Collections.synchronizedList(new ArrayList<>());
                    MemberForm.memberForm.getMemberTabImportProgressBar().setVisible(true);
                    MemberForm.memberForm.getMemberTabImportProgressBar().setIndeterminate(true);
                    for (String toExportFilePath : toImportFilePathList) {
                        // 可以解决中文乱码问题
                        DataInputStream in = new DataInputStream(new FileInputStream(toExportFilePath));
                        reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                        String[] nextLine;

                        while ((nextLine = reader.readNext()) != null) {
                            PushData.allUser.add(nextLine);
                            MemberForm.memberForm.getMemberTabCountLabel().setText(String.valueOf(PushData.allUser.size()));
                        }
                        MemberForm.memberForm.getMemberTabImportProgressBar().setMaximum(100);
                        MemberForm.memberForm.getMemberTabImportProgressBar().setValue(100);
                        MemberForm.memberForm.getMemberTabImportProgressBar().setIndeterminate(false);
                    }
                    JOptionPane.showMessageDialog(MainWindow.mainWindow.getSettingPanel(), "导入完成！", "完成",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(MainWindow.mainWindow.getSettingPanel(), "请至少选择一个！", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception e1) {
                JOptionPane.showMessageDialog(MainWindow.mainWindow.getSettingPanel(), "导入失败！\n\n" + e1.getMessage(), "失败",
                        JOptionPane.ERROR_MESSAGE);
                logger.error(e1);
            } finally {
                MemberForm.memberForm.getMemberTabImportProgressBar().setMaximum(100);
                MemberForm.memberForm.getMemberTabImportProgressBar().setValue(100);
                MemberForm.memberForm.getMemberTabImportProgressBar().setIndeterminate(false);
                MemberForm.memberForm.getMemberTabImportProgressBar().setVisible(false);
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e1) {
                        logger.error(e1);
                        e1.printStackTrace();
                    }
                }
            }

        }));

    }

    /**
     * 切换全选/全不选
     *
     * @return
     */
    private static void toggleSelectAll() {
        if (!selectAllToggle) {
            selectAllToggle = true;
        } else {
            selectAllToggle = false;
        }
    }
}
