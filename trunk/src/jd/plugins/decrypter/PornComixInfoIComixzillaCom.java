package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.List;

import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.controller.LazyPlugin;

import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.hoster.DirectHTTP;

@DecrypterPlugin(revision = "$Revision: 49704 $", interfaceVersion = 3, names = {}, urls = {})
public class PornComixInfoIComixzillaCom extends PluginForDecrypt {
    @Override
    public LazyPlugin.FEATURE[] getFeatures() {
        return new LazyPlugin.FEATURE[] { LazyPlugin.FEATURE.XXX };
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForDecrypt, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "porncomix.online", "comixzilla.com", "ilikecomix.io" });
        return ret;
    }

    private List<String> getDeadDomains() {
        final ArrayList<String> deadDomains = new ArrayList<String>();
        deadDomains.add("ilikecomix.io"); // 2024-09-04
        return deadDomains;
    }

    public static String[] getAnnotationNames() {
        return buildAnnotationNames(getPluginDomains());
    }

    @Override
    public String[] siteSupportedNames() {
        return buildSupportedNames(getPluginDomains());
    }

    public static String[] getAnnotationUrls() {
        return buildAnnotationUrls(getPluginDomains());
    }

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + "/comic/([\\w-]+)/([\\w-]+)/?");
        }
        return ret.toArray(new String[0]);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        br.setFollowRedirects(true);
        String contenturl = param.getCryptedUrl();
        for (final String deadDomain : getDeadDomains()) {
            contenturl = contenturl.replace(deadDomain, this.getHost());
        }
        br.getPage(contenturl);
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String urltitle = new Regex(br.getURL(), "/([^/]+)/?$").getMatch(0);
        /* Allow to pickup quotes */
        String postTitle = br.getRegex("<title>Reading ([^<]+) PornComix Online - Porn Comics</title>").getMatch(0);
        if (StringUtils.isEmpty(postTitle)) {
            /* Fallback */
            postTitle = urltitle.replace("-", " ").trim();
        }
        String[] images = br.getRegex("img id=\"image-\\d+\" src=\"([^\"]+)").getColumn(0);
        if (images == null || images.length == 0) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        for (String imageurl : images) {
            imageurl = imageurl.trim();
            final DownloadLink link = createDownloadlink(DirectHTTP.createURLForThisPlugin(imageurl));
            link.setAvailable(true);
            link.setContainerUrl(param.getCryptedUrl());
            ret.add(link);
        }
        if (postTitle != null) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(postTitle));
            fp.addLinks(ret);
        }
        return ret;
    }
}
