using System.Collections.Generic;
using System.Linq;
using System.Linq.Expressions;

namespace System.Reflection
{
public static class NoroHelper
{
	public const BindingFlags AnyVisibility = BindingFlags.Public | BindingFlags.NonPublic;

	public const BindingFlags Any = BindingFlags.Instance | BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic;


	public static MethodInfo MethodInfo(Expression<Action> expression)
	{
		return (expression.Body as MethodCallExpression)?.Method;
	}

	public static Type Type(this string name)
	{
		return AppDomain.CurrentDomain.GetAssemblies().FirstOrDefault((Assembly a) => a.DefinedTypes.Any((TypeInfo t) => t.FullName == name))?.ManifestModule.GetType(name);
	}

	public static FieldInfo Field(this Type type, string name, BindingFlags flags = BindingFlags.Instance | BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic)
	{
		FieldInfo fieldInfo = type?.GetField(name, flags);
		if ((object)fieldInfo == null)
		{
			if ((object)type == null)
			{
				return null;
			}
			Type baseType = type.BaseType;
			if ((object)baseType == null)
			{
				return null;
			}
			fieldInfo = baseType.Field(name, flags);
		}
		return fieldInfo;
	}

	public static PropertyInfo Property(this Type type, string name, BindingFlags flags = BindingFlags.Instance | BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic)
	{
		PropertyInfo propertyInfo = type?.GetProperty(name, flags);
		if ((object)propertyInfo == null)
		{
			if ((object)type == null)
			{
				return null;
			}
			Type baseType = type.BaseType;
			if ((object)baseType == null)
			{
				return null;
			}
			propertyInfo = baseType.Property(name, flags);
		}
		return propertyInfo;
	}

	public static MethodInfo Method(this Type t, string name, IEnumerable<Type> args)
	{
		return t.Method(name, args.ToArray());
	}

	public static MethodInfo Method(this Type t, string name, params Type[] args)
	{
		if (name == null)
		{
			throw new ArgumentNullException("name");
		}
		if (args == null || args.Length == 0)
		{
			return t.GetMethod(name, BindingFlags.Instance | BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic);
		}
		return t.GetMethod(name, BindingFlags.Instance | BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic, null, args, new ParameterModifier[0]);
	}

	public static object ValueOf(this Type type, string name, object value = null)
	{
		FieldInfo fieldInfo = type?.Field(name);
		if (fieldInfo != null)
		{
			return fieldInfo.GetValue(value);
		}
		PropertyInfo propertyInfo = type?.Property(name);
		if (propertyInfo != null)
		{
			return propertyInfo.GetValue(value);
		}
		throw new MemberAccessException($"{name} not found ({type})");
	}

	public static object ValueOf(this object obj, string name)
	{
		return obj?.GetType().ValueOf(name, obj);
	}

	public static T ValueOf<T>(this Type type, string name)
	{
		return (T)(type?.ValueOf(name));
	}

	public static T ValueOf<T>(this object obj, string name)
	{
		return (T)(obj?.ValueOf(name));
	}

	public static void SetField(this Type type, string name, object value)
	{
		type?.Field(name).SetValue(null, value);
	}

	public static void SetField(this object obj, string name, object value)
	{
		obj?.GetType().Field(name).SetValue(obj, value);
	}

	public static object Invoke(this object o, string name, params object[] args)
	{
		if (name == null)
		{
			throw new ArgumentNullException("name");
		}
		return (((o is Type type) ? type : o.GetType()).Method(name, args.Select((object a) => a.GetType())) ?? throw new MissingMethodException(o.GetType().FullName + "." + name)).Invoke(o, args);
	}
}
}
