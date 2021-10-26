package actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.TextAnnotationGutterProvider;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.annotate.AnnotationSource;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

public class CodeBlameAction extends AnAction {
    String gitPathWindow = "D:\\Program Files\\Git\\cmd\\git.exe";
    String gitPathMac="/usr/bin/git";

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        MyRunnable runnable = new MyRunnable(new FinishCallback() {
            @Override
            public void onFinish(List<String> result) {
//                Messages.showMessageDialog(project, Arrays.toString(result.toArray(new String[0])), "blame test", Messages.getInformationIcon());
                final Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
                editor.getGutter().registerTextAnnotation(new TextAnnotationGutterProvider() {
                    private boolean isShow = false;
                    @Override
                    public @Nullable String getLineText(int line, Editor editor) {
                        isShow = true;
                        if (result.size() > line) {
                            return result.get(line);
                        } else {
                            return "";
                        }
                    }

                    @Override
                    public @Nullable String getToolTip(int line, Editor editor) {
                        return "pcmtest";
                    }

                    @Override
                    public EditorFontType getStyle(int line, Editor editor) {
                        return null;
                    }

                    @Override
                    public @Nullable ColorKey getColor(int line, Editor editor) {
                        return AnnotationSource.LOCAL.getColor();
                    }

                    @Override
                    public @Nullable Color getBgColor(int line, Editor editor) {
                        return null;
                    }

                    @Override
                    public List<AnAction> getPopupActions(int line, Editor editor) {
                        List<AnAction> list = new ArrayList<>();
                        list.add(e.getActionManager().getAction("pcm.blame.test"));
                        return list;
                    }

                    @Override
                    public void gutterClosed() {
                        isShow = false;
                    }
                });
            }

            @Override
            public void onFailed(String errorMsg) {
                Messages.showMessageDialog(project, errorMsg, "blame test", Messages.getInformationIcon());
            }
        }, e);
        runnable.run();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
    }

    public static File findGitDir(@Nonnull File file) {
        File gitFile = null;
        while(file.getParentFile() != null) {
            file = file.getParentFile();
            if( file != null && file.isDirectory()) {
                File[] subFiles = file.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return ".git".equals(file.getName());
                    }
                });
                if (subFiles != null && subFiles.length > 0) {
                    gitFile = subFiles[0];
                    break;
                }
            }
        }
        return gitFile == null ? null : gitFile.getParentFile();
    }

    public static int getFirstIndexOfChar(int start, char c, @Nonnull String s) {
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                return i;
            }
        }
        return -1;
    }

    public class MyRunnable implements Runnable {
        private final FinishCallback callback;
        private final AnActionEvent e;
        public MyRunnable(FinishCallback callback, AnActionEvent e) {
            this.callback = callback;
            this.e = e;
        }

        @Override
        public void run() {
            //获取当前操作的类文件
            PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
            //获取当前类文件的路径
            if (psiFile == null) {
                return;
            }
            List<String> annotations = new ArrayList<>();
            String filePath = psiFile.getVirtualFile().getPath();
            File gitDir = findGitDir(new File(filePath));
            Process process = null;
            String cmd = gitPathMac +  " blame " + filePath;
            try {
                process = Runtime.getRuntime().exec(cmd, null, gitDir);

//            InputStream errorStream = process.getErrorStream();
//            InputStreamReader es=new InputStreamReader(errorStream);
//            //用缓冲器读行
//            BufferedReader ebr =new BufferedReader(es);
//            String errorLine = null;
//            StringBuilder errorSB = new StringBuilder();
//            //直到读完为止
//            while((errorLine=ebr.readLine())!=null) {
//                System.out.println(errorLine);
//                errorSB.append(errorLine).append("\n");
//            }
//            if (!errorSB.toString().isEmpty()) {
//                Messages.showMessageDialog(project, errorSB.toString(), title, Messages.getInformationIcon());
//                return;
//            }

                InputStream fis= process.getInputStream();
                //用一个读输出流类去读
                InputStreamReader isr=new InputStreamReader(fis);
                //用缓冲器读行
                BufferedReader br=new BufferedReader(isr);
                String line = null;
                //直到读完为止
                while((line=br.readLine())!=null) {
                    System.out.println(line);
                    if (line.isEmpty()) {
                        annotations.add("");
                        continue;
                    }
                    int left =  getFirstIndexOfChar(0, '(', line);
                    int right = getFirstIndexOfChar(left+1, ')', line);
                    line = line.substring(left + 1, right);
                    String[] temps = line.trim().split(" ");
                    int count = 0;
                    String toAdd = "";
                    for (int i = 0; i < temps.length; i++) {
                        if (!temps[i].isEmpty() && count < 2) {
                            toAdd += temps[i] + ' ';
                            count++;
                        } else if (count == 2) {
                            annotations.add(toAdd);
                            break;
                        }
                    }
                    if (count < 2 && callback != null) {
                        callback.onFailed("annotation length error!");
                    }
                }
                if (callback != null) {
                    callback.onFinish(annotations);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public interface FinishCallback {
        public void onFinish(List<String> result);
        public void onFailed(String errorMsg);
    }
}
