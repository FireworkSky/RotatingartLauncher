using Microsoft.Xna.Framework;

namespace Localizer
{
public class Hooks
{
	public delegate void BeforeModCtorHandler(object mod);

	public delegate void BeforeLoadHandler();

	public delegate void BeforeSetupContentHandler();

	public delegate void PostSetupContentHandler();

	public delegate void GameUpdateHandler(GameTime gameTime);

	public delegate void PostDrawHandler(GameTime gameTime);

	public static event BeforeModCtorHandler BeforeModCtor;

	public static event BeforeLoadHandler BeforeLoad;

	public static event BeforeSetupContentHandler BeforeSetupContent;

	public static event PostSetupContentHandler PostSetupContent;

	public static event GameUpdateHandler GameUpdate;

	public static event PostDrawHandler PostDraw;

	internal static void InvokeBeforeModCtor(object mod)
	{
		BeforeModCtor?.Invoke(mod);
	}

	internal static void InvokeBeforeLoad()
	{
		BeforeLoad?.Invoke();
	}

	internal static void InvokeBeforeSetupContent()
	{
		BeforeSetupContent?.Invoke();
	}

	internal static void InvokePostSetupContent()
	{
		PostSetupContent?.Invoke();
	}

	internal static void InvokeOnGameUpdate(GameTime gameTime)
	{
		GameUpdate?.Invoke(gameTime);
	}

	internal static void InvokeOnPostDraw(GameTime gameTime)
	{
		PostDraw?.Invoke(gameTime);
	}
}
}
