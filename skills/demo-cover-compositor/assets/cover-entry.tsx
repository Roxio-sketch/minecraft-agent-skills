import {Composition, registerRoot} from 'remotion';
import {CoverTemplate} from './CoverTemplate';

const Root = () => <Composition id="SkillCover" component={CoverTemplate}
  durationInFrames={1} fps={30} width={1920} height={1080}
  defaultProps={{title:'自定义效果', subtitle:'开源 Mod · Skill', shots:[]}} />;
registerRoot(Root);
