using SteamKit2;
using SteamKit2.Internal;

namespace TmlAndroidContent.Steam;

public sealed record SteamWorkshopItem(
    ulong PublishedFileId,
    /// <summary>内容句柄（hcontent_file）：工坊物品实际就是从 CDN 拉这个 manifest。</summary>
    ulong ContentHandle,
    string Title,
    string Description,
    string FileName,
    long FileSize,
    string PreviewUrl,
    uint TimeUpdated,
    uint Subscriptions,
    uint Favorited,
    uint VotesUp,
    IReadOnlyList<string> Tags);

/// <summary>
/// 创意工坊查询（匿名即可）。内置 app 由调用方传，惯例是 tModLoader 1281930。
/// 详情/列表都走 IPublishedFileService 统一消息，跟游戏内 SteamPatch bridge 用的是同一套。
/// </summary>
public static class SteamWorkshopService
{
    private static readonly SemaphoreSlim Gate = new(1, 1);

    private static PublishedFile ServiceFor(SteamSession session) =>
        session.Client.GetHandler<SteamUnifiedMessages>()!.CreateService<PublishedFile>();

    public static async Task<IReadOnlyList<SteamWorkshopItem>> QueryAsync(
        SteamSession session,
        uint appId,
        string? search,
        uint page,
        uint countPerPage,
        CancellationToken cancellationToken = default)
    {
        var request = new CPublishedFile_QueryFiles_Request
        {
            query_type = 1,               // 按发布时间排（原生 EUGCQuery 对应值）
            page = page,
            numperpage = countPerPage == 0 ? 20 : countPerPage,
            creator_appid = appId,
            appid = appId,
            filetype = 0,
            return_vote_data = true,
            return_tags = true,
            return_kv_tags = true,
            return_previews = true,
            return_metadata = true,
            return_short_description = true,
        };
        if (!string.IsNullOrWhiteSpace(search))
            request.search_text = search;

        SteamUnifiedMessages.ServiceMethodResponse<CPublishedFile_QueryFiles_Response> response;
        await Gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            response = await ServiceFor(session).QueryFiles(request).ToTask()
                .WaitAsync(cancellationToken).ConfigureAwait(false);
        }
        finally
        {
            Gate.Release();
        }

        if (response.Result != EResult.OK)
            throw new InvalidOperationException($"Steam workshop query failed: {response.Result}");

        return response.Body.publishedfiledetails.Select(Map).ToList();
    }

    public static async Task<IReadOnlyList<SteamWorkshopItem>> GetDetailsAsync(
        SteamSession session,
        uint appId,
        IEnumerable<ulong> publishedFileIds,
        CancellationToken cancellationToken = default)
    {
        var request = new CPublishedFile_GetDetails_Request
        {
            appid = appId,
            short_description = true,
            includevotes = true,
            includekvtags = true,
            includemetadata = true,
        };
        foreach (var id in publishedFileIds)
            request.publishedfileids.Add(id);

        SteamUnifiedMessages.ServiceMethodResponse<CPublishedFile_GetDetails_Response> response;
        await Gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            response = await ServiceFor(session).GetDetails(request).ToTask()
                .WaitAsync(cancellationToken).ConfigureAwait(false);
        }
        finally
        {
            Gate.Release();
        }

        if (response.Result != EResult.OK)
            throw new InvalidOperationException($"Steam workshop details failed: {response.Result}");

        return response.Body.publishedfiledetails.Select(Map).ToList();
    }

    private static SteamWorkshopItem Map(PublishedFileDetails detail) => new(
        detail.publishedfileid,
        detail.hcontent_file,
        detail.title ?? string.Empty,
        string.IsNullOrEmpty(detail.short_description) ? detail.file_description ?? string.Empty : detail.short_description,
        detail.filename ?? string.Empty,
        (long)detail.file_size,
        string.IsNullOrEmpty(detail.preview_url) ? string.Empty : detail.preview_url,
        detail.time_updated,
        detail.subscriptions,
        detail.favorited,
        detail.vote_data?.votes_up ?? 0,
        detail.tags?.Select(tag => tag.tag ?? string.Empty).ToList() ?? []);
}
