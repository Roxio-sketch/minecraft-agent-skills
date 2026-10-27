import {AbsoluteFill, Img, staticFile} from 'remotion';

type Shot = {
  src: string;
  box: [number, number, number, number];
  image: [number, number, number, number];
  brightness?: number;
};
export type CoverProps = {
  title: string;
  subtitle: string;
  shots: Shot[];
  mainSize?: number;
  secondarySize?: number;
  mainTop?: number;
  secondaryTop?: number;
  mainColor?: string;
  fontFamily?: string;
};

export const CoverTemplate = ({title, subtitle, shots, mainSize=252,
  secondarySize=136, mainTop=310, secondaryTop=655,
  mainColor='#FFEE00', fontFamily='"Microsoft YaHei", "Noto Sans CJK SC", sans-serif'}: CoverProps) => (
  <AbsoluteFill style={{background:'#080C16', fontFamily}}>
    {shots.map((shot, index) => (
      <div key={index} style={{position:'absolute', left:shot.box[0], top:shot.box[1],
        width:shot.box[2], height:shot.box[3], overflow:'hidden'}}>
        <Img src={staticFile(shot.src)} style={{position:'absolute', left:shot.image[0],
          top:shot.image[1], width:shot.image[2], height:shot.image[3], maxWidth:'none',
          filter:`brightness(${shot.brightness ?? 1})`}} />
      </div>
    ))}
    <div style={{position:'absolute', left:0, width:'100%', top:mainTop,
      fontSize:mainSize, fontWeight:900, lineHeight:1.12, textAlign:'center',
      whiteSpace:'nowrap', color:mainColor, WebkitTextStroke:'7px #080B11',
      paintOrder:'stroke fill', textShadow:'0 6px 5px rgba(0,0,0,.95)'}}>{title}</div>
    <div style={{position:'absolute', left:0, width:'100%', top:secondaryTop,
      fontSize:secondarySize, fontWeight:900, lineHeight:1.12, textAlign:'center',
      whiteSpace:'nowrap', color:'white', WebkitTextStroke:'7px #080B11',
      paintOrder:'stroke fill', textShadow:'0 6px 5px rgba(0,0,0,.95)'}}>{subtitle}</div>
  </AbsoluteFill>
);
