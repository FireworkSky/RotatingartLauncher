using System;

namespace RAL.Bootstrap.CoreCLR;

/// <summary>
/// 补丁 API 版本标签 / Patch API version label
///
/// Bootstrap 只加载带有受支持版本标签的补丁（当前仅 1.0）。
/// The Bootstrap only loads patches labelled with a supported
/// API version (currently 1.0 only).
/// </summary>
[AttributeUsage(AttributeTargets.Class, Inherited = false)]
// ReSharper disable once ClassNeverInstantiated.Global
public sealed class RALPatchAttribute(int major, int minor) : Attribute
{
    /// <summary>补丁 API 主版本 / Patch API major version</summary>
    public int Major { get; } = major;

    /// <summary>补丁 API 次版本 / Patch API minor version</summary>
    public int Minor { get; } = minor;
}
