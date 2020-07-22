import com.intellij.ide.IdeView;
import com.intellij.ide.actions.CreateFileAction;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ComponentCreator extends AnAction {

    public ComponentCreator() {
        super();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        final IdeView view = event.getData(LangDataKeys.IDE_VIEW);
        final Project project = event.getData(CommonDataKeys.PROJECT);
        if (view == null || project == null) return;

        final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);
        String moduleName;
        String componentName;
        String libraryName;
        CreateFileAction.MkDirs mkdirs;
        VirtualFile virt;

        String message = "Введите название библиотеки и нового компонента через /";
        String title = "Создание библиотеки с Wasaby компонентом";
        if (directory.getName().startsWith("_")) {
            message = "Введите название нового компонента";
            title = "Создание Wasaby компонента внутри библиотеки";
        }
        String newName = Messages.showInputDialog(message, title, Messages.getQuestionIcon());
        if (newName != null && directory != null) {
            if (directory.getName().startsWith("_")) {
                // создание модуля в библиотеке
                if (newName.contains("/")) {
                    // библиотека в библиотеке ??????
                    Messages.showWarningDialog("Библиотека в библиотеке? Meh.", "Библиотечный Варнинг");
                    return;
                } else {
                    // новый модуль
                    mkdirs = new CreateFileAction.MkDirs(newName, directory);
                    virt = mkdirs.directory.getVirtualFile();
                    moduleName = getModuleName(virt);
                    // если название библиотеки написали с _ то забьем на это для дальнейшего удобства
                    libraryName = virt.getName().startsWith("_") ? virt.getName().substring(1) : virt.getName();
                    componentName = newName;
                }
            } else {
                // создание вне библиотеки
                if (newName.contains("/")) {
                    mkdirs = new CreateFileAction.MkDirs("_" + newName, directory);
                    virt = mkdirs.directory.getVirtualFile();
                    moduleName = getModuleName(virt);
                    String[] arr = newName.split("/");
                    // если название библиотеки написали с _ то забьем на это для дальнейшего удобства
                    libraryName = arr[0].startsWith("_") ? arr[0].substring(1) : arr[0];
                    componentName = arr[1];
                } else {
                    // модуль вне библиотеки:???
                    Messages.showWarningDialog("Модуль вне библиотеки? Meh.", "Библиотечный Варнинг");
                    return;
                }
            }

            // в названии модуля первая буква всегда большая
            String finalComponentName = componentName.substring(0, 1).toUpperCase() + componentName.substring(1);
            // в названии библиотеки первая буква всегда маленькая
            String finalLibraryName = libraryName.substring(0, 1).toLowerCase() + libraryName.substring(1);

            try {
                // ./Module.ts
                WriteAction.compute(() -> mkdirs.directory.createFile(finalComponentName + ".ts"));

                // ./Module/Module.wml
                // ./Module/Module.less
                VirtualFile insideDir;
                if (virt.findChild(finalComponentName) != null) {
                    insideDir = virt.findChild(finalComponentName);
                } else {
                    insideDir = WriteAction.compute(() -> virt.createChildDirectory(null, finalComponentName));
                }
                WriteAction.compute(() -> insideDir.findOrCreateChildData(null, finalComponentName + ".wml"));
                WriteAction.compute(() -> insideDir.findOrCreateChildData(null, "_" + finalComponentName + ".less"));

                // ./interface/IModule.ts
                VirtualFile interfaceDir;
                if (virt.findChild("interface") != null) {
                    interfaceDir = virt.findChild("interface");
                } else {
                    interfaceDir = WriteAction.compute(() -> virt.createChildDirectory(null, "interface"));
                }
                WriteAction.compute(() -> interfaceDir.findOrCreateChildData(null, "I" + finalComponentName + ".ts"));

                WriteAction.compute(() -> {
                    VirtualFile file = virt.findChild(mkdirs.newName + ".ts");
                    try {
                        // заполнение файлов компонента
                        // ./Module.ts
                        assert file != null;
                        OutputStream os = file.getOutputStream(null);
                        os.write(replacePlaceholders(getContentTSFile(), moduleName, finalLibraryName, finalComponentName).getBytes(StandardCharsets.UTF_8));
                        os.close();


                        // ./interface/IModule.ts
                        file = interfaceDir.findChild("I" + finalComponentName + ".ts");
                        assert file != null;
                        os = file.getOutputStream(null);
                        os.write(replacePlaceholders(getContentInterfaceFile(), moduleName, finalLibraryName, finalComponentName).getBytes(StandardCharsets.UTF_8));
                        os.close();

                        // ./Module/Module.wml
                        file = insideDir.findChild(finalComponentName + ".wml");
                        assert file != null;
                        os = file.getOutputStream(null);
                        os.write(replacePlaceholders(getContentWML(), moduleName, finalLibraryName, finalComponentName).getBytes(StandardCharsets.UTF_8));
                        os.close();

                        // ./Module/Module.less
                        file = insideDir.findChild("_" + finalComponentName + ".less");
                        assert file != null;
                        os = file.getOutputStream(null);
                        os.write(replacePlaceholders(getContentLess(), moduleName, finalLibraryName, finalComponentName).getBytes(StandardCharsets.UTF_8));
                        os.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return file;
                });

            } catch (IOException e) {
                e.printStackTrace();
            }

            // создание и дополнение библиотечного файла
            if (virt.getParent().findChild(finalLibraryName + ".ts") == null) {
                // создание новой библиотеки
                WriteAction.compute(() -> mkdirs.directory.getParent().createFile(finalLibraryName + ".ts"));
                WriteAction.compute(() -> mkdirs.directory.getParent().createFile(finalLibraryName + ".less"));
                WriteAction.compute(() -> {
                    VirtualFile file = virt.getParent().findChild(finalLibraryName + ".ts");
                    try {
                        assert file != null;
                        OutputStream os = file.getOutputStream(null);
                        os.write(replacePlaceholders(getContentLibraryTSFile(), moduleName, finalLibraryName, finalComponentName).getBytes(StandardCharsets.UTF_8));
                        os.close();

                        file = virt.getParent().findChild(finalLibraryName + ".less");
                        assert file != null;
                        os = file.getOutputStream(null);
                        os.write(replacePlaceholders(getContentLibraryLessFile(), moduleName, finalLibraryName, finalComponentName).getBytes(StandardCharsets.UTF_8));
                        os.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    return file;
                });
            } else {
                WriteAction.compute(() -> {
                    VirtualFile vfile = virt.getParent().findChild(finalLibraryName + ".ts");
                    try {
                        assert vfile != null;
                        String fileContent = FileUtil.loadTextAndClose(vfile.getInputStream());
                        fileContent = fileContent.concat(getAdditionalContentLibraryTSFile());
                        OutputStream os = vfile.getOutputStream(null);
                        os.write(replacePlaceholders(fileContent, moduleName, finalLibraryName, finalComponentName).getBytes(StandardCharsets.UTF_8));
                        os.close();

                        vfile = virt.getParent().findChild(finalLibraryName + ".less");
                        assert vfile != null;
                        fileContent = FileUtil.loadTextAndClose(vfile.getInputStream());
                        fileContent = fileContent.concat(getContentLibraryLessFile());
                        os = vfile.getOutputStream(null);
                        os.write(replacePlaceholders(fileContent, moduleName, finalLibraryName, finalComponentName).getBytes(StandardCharsets.UTF_8));
                        os.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                   return vfile;
                });
            }
        }
    }

    // попытка найти .s3mod чтоб правильный путь до модуля написать
    private String getModuleName(VirtualFile virt) {
        String moduleName = virt.getParent().getName();
        StringBuilder pathTos3mod = new StringBuilder();
        while (virt.getParent() != null) {
            virt = virt.getParent();
            pathTos3mod.insert(0, "/" + virt.getName());
            if (virt.findChild(virt.getName() + ".s3mod") != null) {
                moduleName = pathTos3mod.substring(1);
            }
        }
        return moduleName;
    }

    private String replacePlaceholders(String inputStr, String moduleName, String libraryName, String componentName) {
        inputStr = inputStr.replaceAll("<ModuleName>", moduleName);
        inputStr = inputStr.replaceAll("<LibraryName>", libraryName);
        inputStr = inputStr.replaceAll("<ComponentName>", componentName);
        return inputStr;
    }

    private String getContentLibraryTSFile() {
        return "/**\n" +
                " *\n" +
                " * @library <ModuleName>/<LibraryName>\n" +
                " * @includes <ComponentName> <ModuleName>/_<LibraryName>/<ComponentName>\n" +
                " * @public\n" +
                " * @author\n" +
                " */\n" +
                "\n" +
                "export {default as <ComponentName>} from '<ModuleName>/_<LibraryName>/<ComponentName>';\n";
    }

    private String getAdditionalContentLibraryTSFile() {
        return "export {default as <ComponentName>} from '<ModuleName>/_<LibraryName>/<ComponentName>';\n";
    }

    private String getContentLibraryLessFile() {
        return "@import '_<LibraryName>/<ComponentName>/_<ComponentName>';\n";
    }

    private String getContentTSFile() {
        return "import {Control, TemplateFunction} from 'UI/Base';\n" +
                "import * as template from 'wml!<ModuleName>/_<LibraryName>/<ComponentName>/<ComponentName>';\n" +
                "import {default as I<ComponentName>Options} from './interface/I<ComponentName>';\n" +
                "\n" +
                "/**\n" +
                "* @class <ModuleName>/<LibraryName>:<ComponentName>\n" +
                "* @extends UI/Base:Control\n" +
                "* @author\n" +
                "* @control\n" +
                "* @public\n" +
                "*/\n" +
                "\n" +
                "export default class <ComponentName> extends Control<I<ComponentName>Options> {\n" +
                "    protected _template: TemplateFunction = template;\n" +
                "\n" +
                "    protected _beforeMount(options: I<ComponentName>Options): Promise<void> | void {\n" +
                "        return;\n" +
                "    }\n" +
                "\n" +
                "    // Подключаем файл стилей для компонента\n" +
                "    static _styles: string[] = ['<ModuleName>/<LibraryName>'];\n" +
                "\n" +
                "    // Подключаем платформенные классы\n" +
                "    static _themes: string[] = ['Controls/Classes'];\n" +
                "\n" +
                "    static getDefaultOptions(): I<ComponentName>Options {\n" +
                "        return {};\n" +
                "    }\n" +
                "}\n";
    }

    private String getContentInterfaceFile() {
        return "import {IControlOptions} from 'UI/Base';\n" +
                "\n" +
                "export default interface I<ComponentName>Options extends IControlOptions {\n" +
                "}\n";
    }

    private String getContentWML() {
        return "<div class=\"<ModuleName>_<LibraryName>_<ComponentName>\"></div>\n";
    }

    private String getContentLess() {
        return ".<ModuleName>_<LibraryName>_<ComponentName> {\n}\n";
    }

}
