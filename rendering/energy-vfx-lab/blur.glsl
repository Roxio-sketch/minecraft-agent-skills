uniform sampler2D image;
uniform vec2 viewport,direction;
out vec4 result;
void main(){vec2 uv=gl_FragCoord.xy/viewport;vec3 col=texture(image,uv).rgb*.227027;
 col+=texture(image,uv+direction*1.384615).rgb*.316216;col+=texture(image,uv-direction*1.384615).rgb*.316216;
 col+=texture(image,uv+direction*3.230769).rgb*.070270;col+=texture(image,uv-direction*3.230769).rgb*.070270;result=vec4(col,1);}
