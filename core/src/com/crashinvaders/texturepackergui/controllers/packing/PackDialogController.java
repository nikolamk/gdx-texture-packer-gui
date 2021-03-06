package com.crashinvaders.texturepackergui.controllers.packing;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.badlogic.gdx.utils.Array;
import com.crashinvaders.texturepackergui.AppConstants;
import com.crashinvaders.texturepackergui.controllers.packing.processors.PackingProcessor;
import com.crashinvaders.texturepackergui.controllers.packing.processors.PngtasticCompressingProcessor;
import com.crashinvaders.texturepackergui.controllers.packing.processors.TinifyCompressingProcessor;
import com.crashinvaders.texturepackergui.controllers.packing.processors.ZopfliCompressingProcessor;
import com.crashinvaders.texturepackergui.events.PackAtlasUpdatedEvent;
import com.crashinvaders.texturepackergui.services.TinifyService;
import com.crashinvaders.texturepackergui.services.model.PackModel;
import com.crashinvaders.texturepackergui.services.model.ProjectModel;
import com.crashinvaders.texturepackergui.services.model.ScaleFactorModel;
import com.crashinvaders.texturepackergui.utils.WidgetUtils;
import com.crashinvaders.texturepackergui.utils.packprocessing.CompositePackProcessor;
import com.crashinvaders.texturepackergui.utils.packprocessing.PackProcessingManager;
import com.crashinvaders.texturepackergui.utils.packprocessing.PackProcessingNode;
import com.crashinvaders.texturepackergui.utils.packprocessing.PackProcessor;
import com.github.czyzby.autumn.annotation.Initiate;
import com.github.czyzby.autumn.annotation.Inject;
import com.github.czyzby.autumn.mvc.component.i18n.LocaleService;
import com.github.czyzby.autumn.mvc.component.ui.InterfaceService;
import com.github.czyzby.autumn.mvc.stereotype.ViewDialog;
import com.github.czyzby.autumn.mvc.stereotype.ViewStage;
import com.github.czyzby.autumn.processor.event.EventDispatcher;
import com.github.czyzby.kiwi.util.common.Exceptions;
import com.github.czyzby.lml.annotation.LmlAction;
import com.github.czyzby.lml.annotation.LmlActor;
import com.github.czyzby.lml.annotation.LmlAfter;
import com.github.czyzby.lml.parser.action.ActionContainer;
import com.kotcrab.vis.ui.FocusManager;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.adapter.ListAdapter;
import com.kotcrab.vis.ui.widget.*;

@ViewDialog(id = "dialog_packing", value = "lml/dialogPacking.lml")
public class PackDialogController implements ActionContainer {
    private static final String PREF_KEY_AUTO_CLOSE = "auto_close_pack_dialog";

    @Inject InterfaceService interfaceService;
    @Inject EventDispatcher eventDispatcher;
    @Inject LocaleService localeService;
    @Inject TinifyService tinifyService;

    @ViewStage Stage stage;
    private Preferences prefs;

    @LmlActor("window") VisDialog window;
    @LmlActor("scrItems") VisScrollPane scrItems;
    @LmlActor("listItems") ListView.ListViewTable<PackProcessingNode> listItems;
    @LmlActor("cbAutoClose") VisCheckBox cbAutoClose;
    @LmlActor("progressBar") VisProgressBar progressBar;
    private VisImageButton btnClose;

    @Initiate
    public void initialize() {
        prefs = Gdx.app.getPreferences(AppConstants.PREF_NAME_COMMON);
    }

    @LmlAfter
    public void initView() {
        btnClose = WidgetUtils.obtainCloseButton(window);
        btnClose.setColor(new Color(0xffffff44));
        btnClose.setDisabled(true);

        stage.setScrollFocus(scrItems);

        cbAutoClose.setChecked(prefs.getBoolean(PREF_KEY_AUTO_CLOSE, false));
    }

    @LmlAction("obtainListAdapter") ListAdapter obtainListAdapter() {
        return new PackProcessingListAdapter(interfaceService);
    }

    @LmlAction("onAutoCloseChecked") void onAutoCloseChecked(VisCheckBox cbAutoClose) {
        prefs.putBoolean(PREF_KEY_AUTO_CLOSE, cbAutoClose.isChecked());
        prefs.flush();
    }

    public void launchPack(ProjectModel project, PackModel pack) {
        launchPack(project, Array.with(pack));
    }

    public void launchPack(ProjectModel project, Array<PackModel> packs) {
        Array<PackProcessingNode> nodes = prepareProcessingNodes(project, packs);

        PackProcessingListAdapter adapter = (PackProcessingListAdapter)listItems.getListView().getAdapter();
        adapter.clear();
        for (PackProcessingNode node : nodes) {
            adapter.add(node);
        }

        PackProcessingManager packProcessingManager = new PackProcessingManager(
                new CompositePackProcessor(
                        new PackingProcessor(),
                        new PngtasticCompressingProcessor(),
                        new ZopfliCompressingProcessor(),
                        new TinifyCompressingProcessor(tinifyService)),
//                new TestProcessor(),
                new PackWorkerListener());

        for (int i = 0; i < nodes.size; i++) {
            PackProcessingNode node = nodes.get(i);
            packProcessingManager.postProcessingNode(node);
        }
        packProcessingManager.execute(project);
    }

    private Array<PackProcessingNode> prepareProcessingNodes(ProjectModel project, Array<PackModel> packs) {
        Array<PackProcessingNode> result = new Array<>();
        for (PackModel pack : packs) {
            for (ScaleFactorModel scaleFactor : pack.getScaleFactors()) {
                PackModel newPack = new PackModel(pack);
                newPack.setScaleFactors(Array.with(scaleFactor));
                TexturePacker.Settings settings = newPack.getSettings();
                settings.scaleSuffix[0] = "";
                settings.scale[0] = scaleFactor.getFactor();

                PackProcessingNode processingNode = new PackProcessingNode(project, newPack);
                processingNode.setOrigPack(pack);

                result.add(processingNode);
            }
        }
        return result;
    }

    /** @return localized string */
    private String getString(String key) {
        return localeService.getI18nBundle().get(key);
    }

    /** @return localized string */
    private String getString(String key, Object... args) {
        return localeService.getI18nBundle().format(key, args);
    }

    private class PackWorkerListener implements PackProcessingManager.Listener {
        final PackProcessingListAdapter adapter;
        boolean errors = false;
        int finishedCounter = 0;

        public PackWorkerListener() {
            adapter = (PackProcessingListAdapter)listItems.getListView().getAdapter();
        }

        @Override
        public void onProcessingStarted() {
            progressBar.setRange(0, adapter.size());
            progressBar.setValue(0);
        }

        @Override
        public void onProcessingFinished() {
            btnClose.setDisabled(false);
            btnClose.setColor(Color.WHITE);

            FocusManager.switchFocus(stage, btnClose);

            window.closeOnEscape();

            if (!errors && cbAutoClose.isChecked()) {
                window.hide();
            }

            // If there is only one pack, show log on error
            if (errors && adapter.size() == 1) {
                adapter.getView(adapter.get(0)).showLogWindow();
            }

            // Indicate total result by changing progress bar color
            {
                ProgressBar.ProgressBarStyle style = new ProgressBar.ProgressBarStyle(progressBar.getStyle());
                Drawable fill = errors ?
                        VisUI.getSkin().getDrawable("progressBarErr") :
                        VisUI.getSkin().getDrawable("progressBarSucc");
                style.knob = fill;
                style.knobBefore = fill;
                progressBar.setStyle(style);
            }
        }

        @Override
        public void onBegin(PackProcessingNode node) {
        }

        @Override
        public void onError(final PackProcessingNode node, Exception e) {
            onFinished(node);

            adapter.getView(node).setToError(e);
            errors = true;
        }

        @Override
        public void onSuccess(PackProcessingNode node) {
            onFinished(node);

            adapter.getView(node).setToSuccess();
        }

        private void onFinished(final PackProcessingNode node) {
            adapter.getView(node).onFinishProcessing();

            finishedCounter += 1;
            progressBar.setValue(finishedCounter);

            // Notification
            Gdx.app.postRunnable(new Runnable() {
                @Override
                public void run() {
                    eventDispatcher.postEvent(new PackAtlasUpdatedEvent(node.getOrigPack()));
                }
            });
        }
    }

    private static class TestProcessor implements PackProcessor {
        @Override
        public void processPackage(PackProcessingNode processingNode) {
            try {
                System.out.println("start processing");
                Thread.sleep(MathUtils.random(500, 2500));
                if (MathUtils.randomBoolean()) throw new RuntimeException();
                System.out.println("finish processing");
            } catch (InterruptedException e) {
                Exceptions.throwRuntimeException(e);
            }
        }
    }
}