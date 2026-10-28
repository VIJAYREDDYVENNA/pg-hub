import ICONS from './iconRegistry';

/**
 * App-wide icon component backed by react-icons (Bootstrap icon set).
 * Accepts the familiar class syntax so icon names can live in data maps:
 *   <Icon className="bi bi-people-fill me-1 text-success" />   or   <Icon name="bi-people-fill" />
 * The "bi-*" token selects the icon; every other class is applied to the SVG.
 * Decorative by default (aria-hidden) unless an aria-label/title is given.
 */
export default function Icon({ name, className = '', title, style, ...rest }) {
  const tokens = String(className).split(/\s+/).filter(Boolean);
  const iconName = name || tokens.find((t) => t.startsWith('bi-'));
  const classes = tokens.filter((t) => t !== 'bi' && t !== iconName);
  const Component = ICONS[iconName];
  const labelled = rest['aria-label'] || title;
  if (!Component) {
    if (process.env.NODE_ENV !== 'production' && iconName) console.warn(`Icon "${iconName}" is not registered in iconRegistry.js`);
    return null;
  }
  return (
    <Component className={['hh-icon', ...classes].join(' ')} style={style} title={title}
      aria-hidden={labelled ? undefined : true} focusable="false" {...rest} />
  );
}
