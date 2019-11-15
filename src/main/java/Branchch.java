import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import git4idea.branch.GitBrancher;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

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
        String endOfBranch = "_";
        try {
            endOfBranch += getFromClipboard();
        } catch (IOException | UnsupportedFlavorException e) {
            e.printStackTrace();
        }
        String typeOfBranch = event.getPresentation().getDescription();
        Project project = event.getProject();
        GitRepositoryManager gitRepositoryManager = GitRepositoryManager.getInstance(project);
        List<GitRepository> repositories = gitRepositoryManager.getRepositories();

        for (GitRepository repository : repositories) {
            String currentBranchName = repository.getCurrentBranch().getName();
            String startOfBranch = currentBranchName.substring(3) + "/" + typeOfBranch + "/";

            if (currentBranchName.startsWith("rc-")) {
                String name = Messages.showInputDialog(
                        "Enter branch name:",
                        startOfBranch + "<your_branch_name>" + endOfBranch,
                        Messages.getQuestionIcon());
                if (name != null) {
                    String newBranchName = startOfBranch + name + endOfBranch;
                    GitBrancher brancher = GitBrancher.getInstance(project);
                    brancher.checkoutNewBranch(newBranchName, repositories);
                }

                // fixme: тут возможно не всегда будет успевать гит создать ветку или упасть с ошибкой
                // doCommitProject();
            }
        }
    }

    private String getFromClipboard() throws IOException, UnsupportedFlavorException {
        return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
    }

    private void doCommitProject() {
        ActionManager am = ActionManager.getInstance();
        am.getAction("CheckinProject").actionPerformed(new AnActionEvent(null, DataManager.getInstance().getDataContext(),
                ActionPlaces.UNKNOWN, new Presentation(),
                ActionManager.getInstance(), 0));
    }

}
