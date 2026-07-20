import {
    ApertureIcon,
    MapIcon,
    MountainIcon,
    BoxIcon,
    LayoutDashboardIcon, LoginIcon, MoodHappyIcon, UserPlusIcon, ReportIcon, SettingsAutomationIcon
} from 'vue-tabler-icons';

export interface menu {
    header?: string;
    title?: string;
    icon?: any;
    to?: string;
    chip?: string;
    chipColor?: string;
    chipVariant?: string;
    chipIcon?: string;
    children?: menu[];
    disabled?: boolean;
    type?: string;
    subCaption?: string;
}

const sidebarItem: menu[] = [
    { header: 'Home' },
    {
        title: 'Dashboard',
        icon: LayoutDashboardIcon,
        to: '/'
    },
    { header: 'utilities' },
    {
        title: 'Projects',
        icon: ReportIcon,
        to: '/ui/projects'
    },
    {
        title: 'Imagery layers',
        icon: MapIcon,
        to: '/ui/layers'
    },
    {
        title: 'Terrain layers',
        icon: MountainIcon,
        to: '/ui/terrain'
    },
    {
        title: '3D Tiles layers',
        icon: BoxIcon,
        to: '/ui/3dtiles'
    },
    {
        title: 'Process Jobs',
        icon: SettingsAutomationIcon,
        to: '/ui/jobs'
    },
    // { header: 'auth' },
    // {
    //     title: 'Login',
    //     icon: LoginIcon,
    //     to: '/auth/login'
    // },
    // {
    //     title: 'Register',
    //     icon: UserPlusIcon,
    //     to: '/auth/register'
    // },
    // { header: 'Extra' },
    // {
    //     title: 'Icons',
    //     icon: MoodHappyIcon,
    //     to: '/icons'
    // },
    // {
    //     title: 'Sample Page',
    //     icon: ApertureIcon,
    //     to: '/sample-page'
    // },
];

export default sidebarItem;
