package com.app.ralaunch.shared.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.ralaunch.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * 关于页面状态
 */
data class AboutState(
    val appVersion: String = "Unknown",
    val buildInfo: String = "",
    val updateAvailable: Boolean = false
)

/**
 * 社区链接
 */
data class CommunityLink(
    val name: String,
    val icon: ImageVector,
    val url: String
)

/**
 * 贡献者信息
 */
data class Contributor(
    val name: String,
    val role: String,
    val githubUrl: String
)

/**
 * 关于设置内容 - 跨平台
 */
@Composable
fun AboutSettingsContent(
    state: AboutState,
    onCheckUpdateClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onSponsorsClick: () -> Unit,
    onCommunityLinkClick: (String) -> Unit,
    onContributorClick: (String) -> Unit,
    onAppInfoCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val communityLinks = listOf(
        CommunityLink(stringResource(Res.string.about_discord_community), Icons.Default.Forum, "https://discord.gg/cVkrRdffGp"),
        CommunityLink(stringResource(Res.string.about_qq_group), Icons.Default.Group, "https://qm.qq.com/q/BWiPSj6wWQ"),
        CommunityLink(stringResource(Res.string.about_github), Icons.Default.Code, "https://github.com/FireworkSky/RotatingartLauncher")
    )

    val sponsorLinks = listOf(
        CommunityLink(stringResource(Res.string.about_afdian_sponsor), Icons.Default.Favorite, "https://afdian.com/a/RotatingartLauncher"),
        CommunityLink(stringResource(Res.string.about_patreon_sponsor), Icons.Default.Star, "https://www.patreon.com/c/RotatingArtLauncher")
    )

    val contributors = listOf(
        Contributor("FireworkSky", stringResource(Res.string.about_project_author), "https://github.com/FireworkSky"),
        Contributor("LaoSparrow", stringResource(Res.string.about_core_developer), "https://github.com/LaoSparrow")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 应用信息
        AppInfoSection(
            appVersion = state.appVersion,
            buildInfo = state.buildInfo,
            onCheckUpdateClick = onCheckUpdateClick,
            onAppInfoCardClick = onAppInfoCardClick
        )

        // 社区链接
        CommunitySection(
            communityLinks = communityLinks,
            onLinkClick = onCommunityLinkClick
        )

        // 赞助支持
        SponsorSection(
            sponsorLinks = sponsorLinks,
            onSponsorsClick = onSponsorsClick,
            onLinkClick = onCommunityLinkClick
        )

        // 贡献者
        ContributorsSection(
            contributors = contributors,
            onContributorClick = onContributorClick
        )

        // 开源信息
        OpenSourceSection(
            onLicenseClick = onLicenseClick
        )
    }
}

@Composable
private fun AppInfoSection(
    appVersion: String,
    buildInfo: String,
    onCheckUpdateClick: () -> Unit,
    onAppInfoCardClick: () -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_about_app_info_section)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAppInfoCardClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${stringResource(Res.string.about_version_label)} $appVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (buildInfo.isNotEmpty()) {
                    Text(
                        text = "${stringResource(Res.string.about_build_label)} $buildInfo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        SettingsDivider()

        ClickableSettingItem(
            title = stringResource(Res.string.settings_about_check_update_title),
            subtitle = stringResource(Res.string.settings_about_check_update_subtitle),
            icon = Icons.Default.Update,
            onClick = onCheckUpdateClick
        )
    }
}

@Composable
private fun CommunitySection(
    communityLinks: List<CommunityLink>,
    onLinkClick: (String) -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_about_community_section)) {
        communityLinks.forEachIndexed { index, link ->
            if (index > 0) {
                SettingsDivider()
            }
            ClickableSettingItem(
                title = link.name,
                subtitle = stringResource(Res.string.settings_about_community_subtitle),
                icon = link.icon,
                onClick = { onLinkClick(link.url) }
            )
        }
    }
}

@Composable
private fun SponsorSection(
    sponsorLinks: List<CommunityLink>,
    onSponsorsClick: () -> Unit,
    onLinkClick: (String) -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_about_support_section)) {
        ClickableSettingItem(
            title = stringResource(Res.string.settings_about_sponsor_wall_title),
            subtitle = stringResource(Res.string.settings_about_sponsor_wall_subtitle),
            icon = Icons.Default.People,
            onClick = onSponsorsClick
        )

        sponsorLinks.forEach { link ->
            SettingsDivider()
            ClickableSettingItem(
                title = link.name,
                subtitle = stringResource(Res.string.settings_about_become_sponsor_subtitle),
                icon = link.icon,
                onClick = { onLinkClick(link.url) }
            )
        }
    }
}

@Composable
private fun ContributorsSection(
    contributors: List<Contributor>,
    onContributorClick: (String) -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_about_contributors_section)) {
        contributors.forEachIndexed { index, contributor ->
            if (index > 0) {
                SettingsDivider()
            }
            ClickableSettingItem(
                title = contributor.name,
                subtitle = contributor.role,
                icon = Icons.Default.Person,
                onClick = { onContributorClick(contributor.githubUrl) }
            )
        }
    }
}

@Composable
private fun OpenSourceSection(
    onLicenseClick: () -> Unit
) {
    SettingsSection(title = stringResource(Res.string.settings_about_open_source_section)) {
        ClickableSettingItem(
            title = stringResource(Res.string.settings_open_source_licenses),
            subtitle = stringResource(Res.string.settings_about_open_source_subtitle),
            icon = Icons.Default.Description,
            onClick = onLicenseClick
        )
    }
}
