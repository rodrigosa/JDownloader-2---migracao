//jDownloader - Downloadmanager
//Copyright (C) 2017  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.controller.LazyPlugin;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.HTMLSearch;
import jd.plugins.Account;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision: 51582 $", interfaceVersion = 3, names = {}, urls = {})
public class YourporntubeCom extends PluginForHost {
    public YourporntubeCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public LazyPlugin.FEATURE[] getFeatures() {
        return new LazyPlugin.FEATURE[] { LazyPlugin.FEATURE.XXX };
    }

    private String dllink = null;

    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        return true;
    }

    public int getMaxChunks(final DownloadLink link, final Account account) {
        return 0;
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "yourporntube.com" });
        return ret;
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

    private static final Pattern PATTERN_NORMAL    = Pattern.compile("/video/(\\d+)(/([a-z0-9\\-]+)/?)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_EMBED     = Pattern.compile("/embed/([a-f0-9]{20})", Pattern.CASE_INSENSITIVE);
    private static final String  PROPERTY_VIDEO_ID = "video_id";

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + "(" + PATTERN_NORMAL.pattern() + "|" + PATTERN_EMBED.pattern() + ")");
        }
        return ret.toArray(new String[0]);
    }

    @Override
    public String getAGBLink() {
        return "https://www." + getHost() + "/feedback";
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String linkid = getFID(link);
        if (linkid != null) {
            return this.getHost() + "://" + linkid;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getFID(final DownloadLink link) {
        String fid = link.getStringProperty(PROPERTY_VIDEO_ID);
        if (fid != null) {
            /* Return internal video_id stored as plugin property. */
            return fid;
        }
        fid = new Regex(link.getPluginPatternMatcher(), PATTERN_NORMAL).getMatch(0);
        if (fid != null) {
            return fid;
        }
        fid = new Regex(link.getPluginPatternMatcher(), PATTERN_EMBED).getMatch(0);
        return fid;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        dllink = null;
        final String extDefault = ".mp4";
        if (!link.isNameSet()) {
            link.setName(this.getFID(link) + extDefault);
        }
        this.setBrowserExclusive();
        br.getPage(link.getPluginPatternMatcher());
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (StringUtils.containsIgnoreCase(br.getURL(), "/notfound/video_missing")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String urlSlug = null;
        String title = null;
        final Regex regex_type_normal = new Regex(br._getURL().getPath(), PATTERN_NORMAL);
        String originalUrlFromEmbed = null;
        if (regex_type_normal.patternFind()) {
            urlSlug = regex_type_normal.getMatch(2);
            dllink = this.findDirecturl(br);
            title = findTitle(br);
        } else {
            /* PATTERN_EMBED */
            final Regex original_url_info = new Regex(br.getRequest().getHtmlCode(), PATTERN_NORMAL);
            originalUrlFromEmbed = original_url_info.getMatch(-1);
            urlSlug = original_url_info.getMatch(2);
            if (!link.hasProperty(PROPERTY_VIDEO_ID)) {
                /* Find internal video_id for better duplicate matching. */
                String video_id = br.getRegex("video_id\\s*=\\s*\"(\\d+)").getMatch(0);
                if (video_id == null) {
                    video_id = br.getRegex("/video/(\\d+)").getMatch(0);
                }
                if (video_id != null) {
                    link.setProperty(PROPERTY_VIDEO_ID, video_id);
                } else {
                    logger.warning("Failed to find video_id");
                }
            }
            this.dllink = this.findDirecturl(br);
            boolean use_embed_workaround = false;
            if (this.dllink == null) {
                logger.info("Enabled embed workaround because: Failed to find directurl");
                use_embed_workaround = true;
            } else if (!StringUtils.containsIgnoreCase(this.dllink, "md5=") || !StringUtils.containsIgnoreCase(this.dllink, "expires=")) {
                /* 2025-09-26: Embedded items are not playable because required parameters are missing -> Fallback to using normal link */
                logger.info("Enabled embed workaround because: Directurls looks to be broken");
                use_embed_workaround = true;
            }
            if (use_embed_workaround) {
                if (originalUrlFromEmbed != null) {
                    br.getPage(originalUrlFromEmbed);
                    dllink = this.findDirecturl(br);
                    title = findTitle(br);
                } else {
                    logger.warning("Embed workaround not possible because original URL was not found");
                }
            }
        }
        if (title == null && urlSlug != null) {
            title = urlSlug.replace("-", " ").trim();
        }
        if (title != null) {
            title = Encoding.htmlDecode(title);
            title = title.trim();
            link.setFinalFileName(title + extDefault);
        } else {
            logger.warning("Failed to find video title");
        }
        final boolean isDownload = PluginEnvironment.DOWNLOAD.equals(this.getPluginEnvironment());
        if (!isDownload && !StringUtils.isEmpty(dllink)) {
            this.basicLinkCheck(br, br.createHeadRequest(dllink), link, title, extDefault);
        }
        return AvailableStatus.TRUE;
    }

    private String findTitle(final Browser br) {
        return HTMLSearch.searchMetaTag(br, "og:description");
    }

    private String findDirecturl(final Browser br) {
        return br.getRegex("<source src=\"(https?://[^\"]+)\" type=.video/mp4.").getMatch(0);
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link);
        if (StringUtils.isEmpty(dllink)) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(this.br, link, dllink, this.isResumeable(link, null), this.getMaxChunks(link, null));
        handleConnectionErrors(br, dl.getConnection());
        dl.startDownload();
    }

    @Override
    protected void handleConnectionErrors(final Browser br, final URLConnectionAdapter con) throws PluginException, IOException {
        if (!this.looksLikeDownloadableContent(con)) {
            br.followConnection(true);
            if (con.getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
            } else if (con.getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
            } else {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Video broken?");
            }
        }
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void resetPluginGlobals() {
        this.dllink = null;
    }
}