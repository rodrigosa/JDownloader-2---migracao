package jd.controlling.downloadcontroller;

import java.util.List;

import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

import org.appwork.utils.event.Eventsender;
import org.appwork.utils.event.queue.Queue;
import org.jdownloader.controlling.download.DownloadControllerEvent;
import org.jdownloader.controlling.download.DownloadControllerListener;

public interface IDownloadController {
    // DownloadController specific methods
    public void addAllAt(List<FilePackage> fps, int index);

    public void addAll(List<FilePackage> fps);

    public void addListener(DownloadControllerListener l);

    public void addListener(DownloadControllerListener l, boolean weak);

    public void removeListener(DownloadControllerListener l);

    public Eventsender<DownloadControllerListener, DownloadControllerEvent> getEventSender();

    public boolean hasDownloadLinkwithURL(String url);

    public boolean hasDownloadLinkByID(String linkID);

    public DownloadLink getLinkByID(long longID);

    public List<FilePackage> getPackagesCopy();

    public void requestSaving();

    public void checkPluginUpdates();

    // Common PackageController methods
    public List<FilePackage> getPackages();

    public List<DownloadLink> getAllChildren();

    public void removePackage(FilePackage pkg);

    public void removeChildren(List<DownloadLink> removechildren);

    public void clear();

    public int size();

    public Queue getQueue();

    public boolean readLock();

    public void readUnlock(boolean state);

    public void writeLock();

    public void writeUnlock();
}
