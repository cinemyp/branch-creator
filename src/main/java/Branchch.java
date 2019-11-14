import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import git4idea.branch.GitBrancher;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import kotlin.reflect.KFunction;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;


public class Branchch extends AnAction {
    public Branchch() {
        super("Hello");
    }

    public void actionPerformed(AnActionEvent event) {
        String clipboardText = "";
        try {
            clipboardText = getFromClipboard();
        } catch (IOException | UnsupportedFlavorException e) {
            e.printStackTrace();
        }

        Project project = event.getProject();

        GitRepositoryManager gitRepositoryManager = GitRepositoryManager.getInstance(project);
        List<GitRepository> repositories = gitRepositoryManager.getRepositories();
        for (GitRepository repository : repositories) {
            String currentBranchName = repository.getCurrentBranch().getName();
            if (currentBranchName.startsWith("rc-")) {
                String newBranchName = currentBranchName.substring(3) + "/bugfix/" + clipboardText;


                GitBrancher brancher = GitBrancher.getInstance(project);
                brancher.checkoutNewBranch(newBranchName, repositories);

                // fixme: тут возможно не всегда будет успевать гит создать ветку или упасть с ошибкой
                ActionManager am = ActionManager.getInstance();
                am.getAction("CheckinProject").actionPerformed(new AnActionEvent(null, DataManager.getInstance().getDataContext(),
                        ActionPlaces.UNKNOWN, new Presentation(),
                        ActionManager.getInstance(), 0));
            }
        }
    }

    private String getFromClipboard() throws IOException, UnsupportedFlavorException {
        return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
    }

}
