namespace RAL.Bootstrap.CoreCLR;

/// <summary>
/// 补丁基类 / Patch base class
///
/// 补丁程序集编译时需引用 RAL.Bootstrap.CoreCLR.dll
/// （建议 Reference + Private=false，不随补丁复制），
/// 运行时由 Bootstrap 实例化并调用 Initialize()。
/// Patch assemblies must reference RAL.Bootstrap.CoreCLR.dll at compile time
/// (Reference + Private=false recommended, do not copy with the patch);
/// at runtime the Bootstrap instantiates the patch and calls Initialize().
/// </summary>
public abstract class PatchBase
{
    /// <summary>
    /// 补丁入口，在宿主 Main() 之前由 Bootstrap 调用。
    /// Patch entry point, invoked by the Bootstrap before the host Main().
    /// </summary>
    public abstract void Initialize();
}
